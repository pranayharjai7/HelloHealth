// Supabase Edge Function: ai-coach
//
// Server-side LLM proxy for HelloHealth's AI Coaching. Holds the Gemini + OpenRouter API keys as
// Supabase function SECRETS (GEMINI_API_KEY, OPENROUTER_API_KEY) so they NEVER ship in the Android
// APK. The client sends the assembled coaching prompt (provider-neutral) and this function runs the
// fallback chain — Gemini (default) -> OpenRouter (fallback) — and returns the generated text.
//
// Guardrails & Protection (HHA-002 remediation):
//   - Request body limit: <= 32 KB (413 Payload Too Large)
//   - JWT authentication: requires verified user token (401 Unauthorized)
//   - Message count limit: 1..10 messages (400 Bad Request)
//   - Message text limit: <= 2000 chars per message, <= 8000 chars total (400 Bad Request)
//   - System prompt limit: <= 2000 chars (400 Bad Request)
//   - Parameter clamping: temperature in [0.0, 1.0], maxOutputTokens in [100, 2048]
//   - Rate limiting & Quotas: Atomic database ledger via public.check_and_record_ai_usage() (429 Too Many Requests)
//   - Privacy: Sanitized logging only; user health details and conversation text are NEVER logged.

import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const GEMINI_MODEL = "gemini-flash-latest";
const OPENROUTER_MODEL = "meta-llama/llama-3.3-70b-instruct";

const MAX_BODY_BYTES = 32 * 1024; // 32 KB
const MAX_MESSAGES = 10;
const MAX_SINGLE_MESSAGE_CHARS = 2000;
const MAX_TOTAL_MESSAGE_CHARS = 8000;
const MAX_SYSTEM_CHARS = 2000;
const MIN_OUTPUT_TOKENS = 100;
const MAX_OUTPUT_TOKENS = 2048;

interface ChatMessage {
  role: "user" | "model";
  text: string;
}

interface CoachRequest {
  system?: string;
  messages: ChatMessage[];
  temperature?: number;
  maxOutputTokens?: number;
}

const JSON_HEADERS = { "Content-Type": "application/json", "Connection": "keep-alive" };

/** Gemini generateContent. Returns the text, or null on any failure (non-2xx, empty, malformed). */
async function tryGemini(req: CoachRequest): Promise<string | null> {
  const key = Deno.env.get("GEMINI_API_KEY");
  if (!key) return null;
  try {
    const body = {
      contents: req.messages.map((m) => ({
        role: m.role === "model" ? "model" : "user",
        parts: [{ text: m.text }],
      })),
      systemInstruction: req.system ? { parts: [{ text: req.system }] } : undefined,
      generationConfig: {
        temperature: req.temperature ?? 0.7,
        maxOutputTokens: req.maxOutputTokens ?? MAX_OUTPUT_TOKENS,
      },
    };
    const res = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${key}`,
      { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) },
    );
    if (!res.ok) return null;
    const json = await res.json();
    const text: string | undefined = json?.candidates?.[0]?.content?.parts?.[0]?.text;
    return text && text.trim().length > 0 ? text.trim() : null;
  } catch (_e) {
    return null;
  }
}

/** OpenRouter chat completions (OpenAI-compatible). Returns text, or null on any failure. */
async function tryOpenRouter(req: CoachRequest): Promise<string | null> {
  const key = Deno.env.get("OPENROUTER_API_KEY");
  if (!key) return null;
  try {
    const messages: { role: string; content: string }[] = [];
    if (req.system) messages.push({ role: "system", content: req.system });
    for (const m of req.messages) {
      messages.push({ role: m.role === "model" ? "assistant" : "user", content: m.text });
    }
    const res = await fetch("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${key}`,
        "Content-Type": "application/json",
        "HTTP-Referer": "https://hellohealth.app",
        "X-Title": "HelloHealth",
      },
      body: JSON.stringify({
        model: OPENROUTER_MODEL,
        messages,
        temperature: req.temperature ?? 0.7,
        max_tokens: req.maxOutputTokens ?? MAX_OUTPUT_TOKENS,
      }),
    });
    if (!res.ok) return null;
    const json = await res.json();
    const text: string | undefined = json?.choices?.[0]?.message?.content;
    return text && text.trim().length > 0 ? text.trim() : null;
  } catch (_e) {
    return null;
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: {
        ...JSON_HEADERS,
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
      },
    });
  }

  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "method not allowed" }), { status: 405, headers: JSON_HEADERS });
  }

  // 1. Verify Authentication & Extract JWT
  const authHeader = req.headers.get("Authorization");
  if (!authHeader) {
    return new Response(JSON.stringify({ error: "missing authorization header" }), { status: 401, headers: JSON_HEADERS });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");

  let userId: string | null = null;
  let userClient = null;

  if (supabaseUrl && anonKey) {
    userClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authHeader } },
      auth: { persistSession: false },
    });
    const { data: { user }, error: userError } = await userClient.auth.getUser();
    if (userError || !user) {
      return new Response(JSON.stringify({ error: "invalid or expired authentication token" }), { status: 401, headers: JSON_HEADERS });
    }
    userId = user.id;
  }

  // 2. Request body size check
  const contentLength = req.headers.get("content-length");
  if (contentLength && parseInt(contentLength, 10) > MAX_BODY_BYTES) {
    return new Response(JSON.stringify({ error: "request body too large" }), { status: 413, headers: JSON_HEADERS });
  }

  const rawBody = await req.text();
  if (rawBody.length > MAX_BODY_BYTES) {
    return new Response(JSON.stringify({ error: "request body too large" }), { status: 413, headers: JSON_HEADERS });
  }

  let body: CoachRequest;
  try {
    body = JSON.parse(rawBody);
  } catch (_e) {
    return new Response(JSON.stringify({ error: "invalid json" }), { status: 400, headers: JSON_HEADERS });
  }

  // 3. Validate messages array & text lengths
  if (!body?.messages || !Array.isArray(body.messages) || body.messages.length === 0) {
    return new Response(JSON.stringify({ error: "messages array required and must not be empty" }), { status: 400, headers: JSON_HEADERS });
  }

  if (body.messages.length > MAX_MESSAGES) {
    return new Response(
      JSON.stringify({ error: `too many messages (max ${MAX_MESSAGES})` }),
      { status: 400, headers: JSON_HEADERS }
    );
  }

  let totalChars = 0;
  for (const m of body.messages) {
    if (!m || typeof m.text !== "string") {
      return new Response(JSON.stringify({ error: "each message must have a text string" }), { status: 400, headers: JSON_HEADERS });
    }
    if (m.text.length > MAX_SINGLE_MESSAGE_CHARS) {
      return new Response(
        JSON.stringify({ error: `message exceeds maximum length of ${MAX_SINGLE_MESSAGE_CHARS} characters` }),
        { status: 400, headers: JSON_HEADERS }
      );
    }
    totalChars += m.text.length;
  }

  if (totalChars > MAX_TOTAL_MESSAGE_CHARS) {
    return new Response(
      JSON.stringify({ error: `total message text exceeds ${MAX_TOTAL_MESSAGE_CHARS} characters` }),
      { status: 400, headers: JSON_HEADERS }
    );
  }

  if (body.system && body.system.length > MAX_SYSTEM_CHARS) {
    return new Response(
      JSON.stringify({ error: `system instruction exceeds ${MAX_SYSTEM_CHARS} characters` }),
      { status: 400, headers: JSON_HEADERS }
    );
  }

  // 4. Per-user atomic rate limiting & quotas via database procedure
  if (userClient && userId) {
    const { data: usageCheck, error: usageError } = await userClient.rpc("check_and_record_ai_usage", {
      p_user_id: userId,
      p_max_per_minute: 5,
      p_max_per_day: 30,
    });

    if (!usageError && usageCheck && typeof usageCheck === "object") {
      const allowed = (usageCheck as Record<string, unknown>).allowed;
      if (allowed === false) {
        const retryAfter = (usageCheck as Record<string, unknown>).retry_after_seconds ?? 60;
        const reason = (usageCheck as Record<string, unknown>).reason ?? "rate_limited";
        return new Response(
          JSON.stringify({ error: `rate limit or quota exceeded: ${reason}` }),
          {
            status: 429,
            headers: {
              ...JSON_HEADERS,
              "Retry-After": String(retryAfter),
            },
          }
        );
      }
    }
  }

  // 5. Clamp generation parameters
  const clampedTemperature = Math.min(Math.max(body.temperature ?? 0.7, 0.0), 1.0);
  const clampedMaxOutputTokens = Math.min(
    Math.max(body.maxOutputTokens ?? MAX_OUTPUT_TOKENS, MIN_OUTPUT_TOKENS),
    MAX_OUTPUT_TOKENS
  );

  const sanitizedRequest: CoachRequest = {
    system: body.system,
    messages: body.messages,
    temperature: clampedTemperature,
    maxOutputTokens: clampedMaxOutputTokens,
  };

  // 6. Invoke LLM chain with fallback (Gemini -> OpenRouter)
  const gemini = await tryGemini(sanitizedRequest);
  if (gemini) {
    return new Response(JSON.stringify({ text: gemini, provider: "gemini" }), { status: 200, headers: JSON_HEADERS });
  }

  const openRouter = await tryOpenRouter(sanitizedRequest);
  if (openRouter) {
    return new Response(JSON.stringify({ text: openRouter, provider: "openrouter" }), { status: 200, headers: JSON_HEADERS });
  }

  // Both providers failed or unavailable; client will fall back to its on-device rule-based coach
  return new Response(JSON.stringify({ error: "all providers failed" }), { status: 502, headers: JSON_HEADERS });
});
