// Supabase Edge Function: ai-coach
//
// Server-side LLM proxy for HelloHealth's AI Coaching. Holds the Gemini + OpenRouter API keys as
// Supabase function SECRETS (GEMINI_API_KEY, OPENROUTER_API_KEY) so they NEVER ship in the Android
// APK. The client sends the assembled coaching prompt (provider-neutral) and this function runs the
// same fallback chain the app used to run on-device — Gemini (default) -> OpenRouter (fallback) —
// and returns the generated text. The app keeps its on-device rule-based coach for when this
// endpoint itself is unreachable, so coaching still degrades gracefully offline.
//
// Auth: verify_jwt is enabled at deploy time, so only an authenticated HelloHealth user (with a
// valid Supabase session) can invoke it. No extra authz is needed — the coaching context is sent
// by the caller and never touches other users' data.
//
// Contract (request JSON):
//   { system?: string, messages: [{ role: "user"|"model", text: string }],
//     temperature?: number, maxOutputTokens?: number }
// Response JSON:
//   200 { text: string, provider: "gemini"|"openrouter" }
//   502 { error: string }   // both providers failed; client falls back to its rule-based coach

import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const GEMINI_MODEL = "gemini-flash-latest";
const OPENROUTER_MODEL = "meta-llama/llama-3.3-70b-instruct";

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
        maxOutputTokens: req.maxOutputTokens ?? 2048,
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
        max_tokens: req.maxOutputTokens ?? 2048,
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
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "method not allowed" }), { status: 405, headers: JSON_HEADERS });
  }
  let body: CoachRequest;
  try {
    body = await req.json();
  } catch (_e) {
    return new Response(JSON.stringify({ error: "invalid json" }), { status: 400, headers: JSON_HEADERS });
  }
  if (!body?.messages || !Array.isArray(body.messages) || body.messages.length === 0) {
    return new Response(JSON.stringify({ error: "messages required" }), { status: 400, headers: JSON_HEADERS });
  }

  const gemini = await tryGemini(body);
  if (gemini) {
    return new Response(JSON.stringify({ text: gemini, provider: "gemini" }), { status: 200, headers: JSON_HEADERS });
  }
  const openRouter = await tryOpenRouter(body);
  if (openRouter) {
    return new Response(JSON.stringify({ text: openRouter, provider: "openrouter" }), { status: 200, headers: JSON_HEADERS });
  }
  // Both failed — signal the client to use its on-device rule-based coach.
  return new Response(JSON.stringify({ error: "all providers failed" }), { status: 502, headers: JSON_HEADERS });
});
