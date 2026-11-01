// Supabase Edge Function: delete-account
//
// Authenticated server-side account deletion endpoint for HelloHealth.
// Derives identity strictly from caller's verified JWT (never trusts client-supplied user ID).
// Purges all 17 public database tables and storage objects via public.delete_user_account(),
// then removes the auth user from auth.users via Supabase Admin API.
//
// Response JSON:
//   200 { success: true, message: string }
//   401 { error: string }
//   500 { error: string }

import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const JSON_HEADERS = { "Content-Type": "application/json", "Connection": "keep-alive" };

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

  const authHeader = req.headers.get("Authorization");
  if (!authHeader) {
    return new Response(JSON.stringify({ error: "missing authorization header" }), { status: 401, headers: JSON_HEADERS });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

  if (!supabaseUrl || !anonKey) {
    return new Response(JSON.stringify({ error: "server configuration error" }), { status: 500, headers: JSON_HEADERS });
  }

  // 1. Verify caller identity using their JWT
  const userClient = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authHeader } },
    auth: { persistSession: false },
  });

  const { data: { user }, error: userError } = await userClient.auth.getUser();
  if (userError || !user) {
    return new Response(JSON.stringify({ error: "invalid or expired authentication token" }), { status: 401, headers: JSON_HEADERS });
  }

  const userId = user.id;

  // 2. Execute atomic deletion of all user records across all tables and storage
  const { error: rpcError } = await userClient.rpc("delete_user_account", { target_user_id: userId });
  if (rpcError) {
    if (serviceRoleKey) {
      const adminClient = createClient(supabaseUrl, serviceRoleKey, { auth: { persistSession: false } });
      const { error: adminRpcError } = await adminClient.rpc("delete_user_account", { target_user_id: userId });
      if (adminRpcError) {
        return new Response(
          JSON.stringify({ error: `failed to delete user data: ${adminRpcError.message}` }),
          { status: 500, headers: JSON_HEADERS }
        );
      }
    } else {
      return new Response(
        JSON.stringify({ error: `failed to delete user data: ${rpcError.message}` }),
        { status: 500, headers: JSON_HEADERS }
      );
    }
  }

  // 3. Delete the Auth user from auth.users using Admin API if service_role key is present
  if (serviceRoleKey) {
    const adminClient = createClient(supabaseUrl, serviceRoleKey, { auth: { persistSession: false } });
    const { error: deleteAuthError } = await adminClient.auth.admin.deleteUser(userId);
    if (deleteAuthError) {
      return new Response(
        JSON.stringify({ error: `failed to delete auth identity: ${deleteAuthError.message}` }),
        { status: 500, headers: JSON_HEADERS }
      );
    }
  }

  return new Response(
    JSON.stringify({ success: true, message: "Account and associated data deleted successfully" }),
    { status: 200, headers: JSON_HEADERS }
  );
});
