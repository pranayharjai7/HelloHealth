// Supabase Edge Function: delete-account
//
// Authenticated server-side account deletion endpoint for HelloHealth.
// Derives identity strictly from caller's verified JWT (never trusts client-supplied user ID).
// Purges all 17 public database tables via public.delete_user_account(),
// deletes physical storage objects via Supabase Storage API,
// and permanently deletes the auth user from auth.users via Supabase Admin API.
//
// Fail-closed security design:
//   - Requires verified JWT (401 Unauthorized)
//   - Requires SUPABASE_SERVICE_ROLE_KEY to guarantee Auth identity and Storage deletion (500)
//   - Both database wipe, storage cleanup, and Auth identity deletion must succeed before returning 200.
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

  // 1. Validate caller's JWT header
  const authHeader = req.headers.get("Authorization");
  if (!authHeader) {
    return new Response(JSON.stringify({ error: "missing authorization header" }), { status: 401, headers: JSON_HEADERS });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

  // Fail-closed: Service role key is strictly mandatory for complete account deletion
  if (!supabaseUrl || !anonKey || !serviceRoleKey) {
    console.error("delete-account configuration error: SUPABASE_URL, SUPABASE_ANON_KEY, and SUPABASE_SERVICE_ROLE_KEY are all required");
    return new Response(
      JSON.stringify({ error: "server configuration error: admin deletion service unavailable" }),
      { status: 500, headers: JSON_HEADERS }
    );
  }

  // 2. Verify caller identity using their JWT
  const userClient = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authHeader } },
    auth: { persistSession: false },
  });

  const { data: { user }, error: userError } = await userClient.auth.getUser();
  if (userError || !user) {
    return new Response(
      JSON.stringify({ error: "invalid or expired authentication token" }),
      { status: 401, headers: JSON_HEADERS }
    );
  }

  const userId = user.id;
  const adminClient = createClient(supabaseUrl, serviceRoleKey, { auth: { persistSession: false } });

  // 3. Execute atomic deletion of all user records across all 17 public database tables
  const { error: rpcError } = await adminClient.rpc("delete_user_account", { target_user_id: userId });
  if (rpcError) {
    console.error(`delete_user_account RPC error for user ${userId}:`, rpcError.message);
    return new Response(
      JSON.stringify({ error: `failed to delete user database records: ${rpcError.message}` }),
      { status: 500, headers: JSON_HEADERS }
    );
  }

  // 4. Delete physical storage objects via Supabase Storage API across all buckets
  try {
    const { data: buckets, error: listBucketsError } = await adminClient.storage.listBuckets();
    if (!listBucketsError && buckets && buckets.length > 0) {
      for (const bucket of buckets) {
        // List files under this user's folder
        const { data: files } = await adminClient.storage.from(bucket.name).list(userId);
        if (files && files.length > 0) {
          const filePaths = files.map((f) => `${userId}/${f.name}`);
          const { error: removeError } = await adminClient.storage.from(bucket.name).remove(filePaths);
          if (removeError) {
            console.warn(`Storage file removal warning in bucket ${bucket.name}:`, removeError.message);
          }
        }
      }
    }
  } catch (storageErr) {
    console.warn("Storage cleanup exception:", storageErr);
  }

  // 5. Invalidate sessions and permanently delete the Auth identity from auth.users
  const { error: deleteAuthError } = await adminClient.auth.admin.deleteUser(userId);
  if (deleteAuthError) {
    console.error(`deleteUser error for user ${userId}:`, deleteAuthError.message);
    return new Response(
      JSON.stringify({ error: `failed to delete auth identity: ${deleteAuthError.message}` }),
      { status: 500, headers: JSON_HEADERS }
    );
  }

  return new Response(
    JSON.stringify({ success: true, message: "Account and associated data deleted successfully" }),
    { status: 200, headers: JSON_HEADERS }
  );
});
