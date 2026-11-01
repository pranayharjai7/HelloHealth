// Supabase Edge Function: delete-account
//
// Authenticated server-side account deletion endpoint for HelloHealth.
// Derives identity strictly from caller's verified JWT (never trusts client-supplied user ID).
// Purges all 17 public database tables via public.delete_user_account(),
// recursively enumerates, paginates, and deletes physical storage objects via Supabase Storage API,
// invalidates user sessions globally, and permanently deletes the auth user from auth.users.
//
// Fail-closed security design:
//   - Requires verified JWT (401 Unauthorized)
//   - Requires SUPABASE_SERVICE_ROLE_KEY to guarantee Auth identity and Storage deletion (500)
//   - Fails closed on any database wipe error, storage enumeration error, or storage removal error
//   - Requires successful Auth identity deletion before returning 200 OK.
//
// Response JSON:
//   200 { success: true, message: string }
//   401 { error: string }
//   500 { error: string }

import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient, SupabaseClient } from "jsr:@supabase/supabase-js@2";

const JSON_HEADERS = { "Content-Type": "application/json", "Connection": "keep-alive" };

/** Recursively enumerates and paginates all files located under a prefix in a storage bucket. */
async function listAllUserFilesRecursively(
  adminClient: SupabaseClient,
  bucketName: string,
  prefix: string
): Promise<string[]> {
  const filePaths: string[] = [];
  const directoriesToScan = [prefix];

  while (directoriesToScan.length > 0) {
    const currentDir = directoriesToScan.pop()!;
    let offset = 0;
    const limit = 100;

    while (true) {
      const { data: items, error } = await adminClient.storage
        .from(bucketName)
        .list(currentDir, {
          limit,
          offset,
          sortBy: { column: "name", order: "asc" },
        });

      if (error) {
        throw new Error(`Failed to list storage objects in bucket '${bucketName}' at path '${currentDir}': ${error.message}`);
      }

      if (!items || items.length === 0) {
        break;
      }

      for (const item of items) {
        const fullPath = currentDir ? `${currentDir}/${item.name}` : item.name;
        // In Supabase storage, folders have id == null
        if (item.id === null) {
          directoriesToScan.push(fullPath);
        } else {
          filePaths.push(fullPath);
        }
      }

      if (items.length < limit) {
        break;
      }
      offset += limit;
    }
  }

  return filePaths;
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

  // 1. Validate caller's JWT header
  const authHeader = req.headers.get("Authorization");
  if (!authHeader) {
    return new Response(JSON.stringify({ error: "missing authorization header" }), { status: 401, headers: JSON_HEADERS });
  }

  const rawToken = authHeader.replace(/^Bearer\s+/i, "").trim();

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

  // 4. Delete physical storage objects via Supabase Storage API across all buckets (Fail-Closed)
  const { data: buckets, error: listBucketsError } = await adminClient.storage.listBuckets();
  if (listBucketsError) {
    console.error("Storage listBuckets error:", listBucketsError.message);
    return new Response(
      JSON.stringify({ error: `failed to query storage buckets: ${listBucketsError.message}` }),
      { status: 500, headers: JSON_HEADERS }
    );
  }

  if (buckets && buckets.length > 0) {
    for (const bucket of buckets) {
      let userFiles: string[] = [];
      try {
        userFiles = await listAllUserFilesRecursively(adminClient, bucket.name, userId);
      } catch (err: any) {
        console.error(`Error enumerating files in bucket ${bucket.name}:`, err.message);
        return new Response(
          JSON.stringify({ error: `storage enumeration failed: ${err.message}` }),
          { status: 500, headers: JSON_HEADERS }
        );
      }

      if (userFiles.length > 0) {
        // Batch remove in chunks of 100
        for (let i = 0; i < userFiles.length; i += 100) {
          const batch = userFiles.slice(i, i + 100);
          const { error: removeError } = await adminClient.storage.from(bucket.name).remove(batch);
          if (removeError) {
            console.error(`Storage file removal error in bucket ${bucket.name}:`, removeError.message);
            return new Response(
              JSON.stringify({ error: `failed to remove storage objects in bucket ${bucket.name}: ${removeError.message}` }),
              { status: 500, headers: JSON_HEADERS }
            );
          }
        }
      }
    }
  }

  // 5. Revoke user sessions globally via server-side Auth Admin API (Idempotent & Fail-Closed)
  // Single server-side revocation call using GoTrueAdminApi.signOut(rawToken, "global").
  // Treats already-revoked/missing sessions (403/404 session_not_found) idempotently to prevent
  // blocking permanent Auth identity deletion.
  const { error: signOutError } = await adminClient.auth.admin.signOut(rawToken, "global");
  if (signOutError) {
    const msg = (signOutError.message || "").toLowerCase();
    const status = (signOutError as any).status;
    const isAlreadyRevoked =
      status === 403 ||
      status === 404 ||
      msg.includes("session_not_found") ||
      msg.includes("session not found") ||
      msg.includes("missing session") ||
      msg.includes("invalid claim") ||
      msg.includes("token is expired");

    if (!isAlreadyRevoked) {
      console.error(`admin.signOut error for user ${userId}:`, signOutError.message);
      return new Response(
        JSON.stringify({ error: `failed to revoke user sessions: ${signOutError.message}` }),
        { status: 500, headers: JSON_HEADERS }
      );
    }
    console.info(`Sessions for user ${userId} were already revoked or not found; proceeding with account deletion.`);
  }

  // 6. Permanently delete the Auth identity from auth.users (Fail-Closed)
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
