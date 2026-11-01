# Consolidated security findings

Audit revision: `73ce32e7545e3eb68b1ddeee7cd0034a183e79e5` on `security/pre-play-store-audit`.

## Severity summary

Counts include findings still open or awaiting deployment/runtime verification; HHA-003 is source-remediated and excluded.

| Critical | High | Medium | Low | Informational |
|---:|---:|---:|---:|---:|
| 0 | 1 | 1 | 2 | 0 |

The audit is **BLOCKED for Play release**. The open findings below require correction or verification. Static scan/build results and scope limits are summarized in [the audit summary](reports/automated-scan-summary.md).

## HHA-001 — Stale-token protections are not reliably fail-closed

- **Severity:** High — release blocker
- **Category:** Privacy, account lifecycle, data retention
- **Status:** Partially mitigated in source; migration and Edge Function require hardening and staging verification.
- **Affected components:** `supabase/functions/delete-account/index.ts`, `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql`, `run_sql_in_supabase.sql`, Android account deletion flow.
- **Evidence / Rerun Analysis:** The new migration adds a tombstone table, active-account RLS predicates, and mutation triggers. However, `public.is_active_account` catches all errors from its `auth.users` lookup and sets `v_exists := true`, which fails open. It is also executable by `anon`, exposing whether a supplied UUID is active. The Storage restrictive policy is installed inside a `DO` block with `EXCEPTION WHEN OTHERS THEN NULL`; a DDL failure is silently swallowed and the migration can continue without the Storage safeguard. The Edge Function builds `userClient` with `persistSession: false` and only a custom Authorization header, then calls `userClient.auth.signOut({ scope: "global" })`. Supabase JS `signOut` reads its stored session; no session is stored on this client, so the call can return without sending a revocation request. Returned-error checking alone does not prove server revocation. Even successful global sign-out cannot invalidate already-issued access JWTs before expiry. RLS tombstones can mitigate that risk only if the helper and policies are successfully installed and fail closed. No database or Edge Function runtime test was available.
- **Implemented in latest source:** Tombstone insertion before row cleanup; active-account checks on 17 public user tables; triggers on text-keyed tables; recursive/paginated Storage enumeration and fail-closed Storage listing/removal; Auth deletion is required before 200.
- **Remaining for Release:** Make active-account checks fail closed; remove anonymous execute access; install the Storage policy without swallowing DDL failures; revoke refresh tokens using the supplied JWT through the server-side Auth API; verify stale-token rejection across Postgres and Storage with synthetic staging accounts; publish the Google Play deletion web resource and complete Data Safety declarations.
- **References:** [Supabase delete objects](https://supabase.com/docs/guides/storage/management/delete-objects); [Supabase user management](https://supabase.com/docs/guides/auth/managing-user-data); [Supabase JavaScript sign-out](https://supabase.com/docs/reference/javascript/auth-signout); [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en).

## HHA-002 — AI coaching quota controls need deployment and runtime verification

- **Severity:** Medium
- **Category:** Backend API resource consumption / provider-cost abuse
- **Status:** Remediated in repository source; staging deployment and verification pending.
- **Affected components:** `supabase/functions/ai-coach/index.ts`, `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql`, `run_sql_in_supabase.sql`.
- **Evidence / Rerun Analysis:** The latest source requires Supabase configuration and a verified caller, streams request bytes with a 32 KB cap, rejects quota RPC errors, and uses per-user transaction advisory locking around quota evaluation and recording.
- **Remediation Implemented:**
  1. Enforced mandatory fail-closed authentication and configuration in `ai-coach/index.ts` (returns 500 if Supabase config is missing; returns 401 if caller JWT is missing or invalid).
  2. Implemented a streaming byte reader `readBodyWithLimit` that enforces the 32 KB limit on the incoming byte stream before buffering the payload into memory, returning 413 immediately.
  3. Made quota enforcement strictly fail-closed: if `check_and_record_ai_usage` returns an error, the request is rejected with HTTP 500 rather than falling through unmetered. If quota is exceeded, returns 429 with `Retry-After`.
  4. Added transactional advisory locking in SQL (`PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(p_user_id))`) to serialize quota evaluations per user and eliminate the concurrency race condition.
- **Remaining for Release:** Deploy to authorized staging and verify RPC failure behavior, oversized/chunked bodies, and concurrent requests. No Edge Function runtime tests or Supabase deployment were performed.
- **References:** [Supabase Edge Function authentication](https://supabase.com/docs/guides/functions/auth); [OWASP API4:2023](https://owasp.org/API-Security/editions/2023/en/0xa4-unrestricted-resource-consumption/).

## HHA-003 — SECURITY DEFINER function search-path hardening

- **Severity:** Medium
- **Category:** Database function hardening
- **Status:** Remediated in repository source; migration deployment pending.
- **Affected components:** `public.delete_user_account(uuid)`, `public.check_and_record_ai_usage(text, integer, integer)`, `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql`, `run_sql_in_supabase.sql`.
- **Evidence / Rerun Analysis:** Both functions now set `search_path = ''`, schema-qualify references, revoke public execute, and grant execution to `authenticated` and `service_role`. This addresses the source-level search-path issue; deployed definitions/grants remain unverified.
- **Remediation Implemented:**
  1. Updated both `delete_user_account` and `check_and_record_ai_usage` to set `SET search_path = ''`.
  2. Schema-qualified every reference and built-in function with `pg_catalog.*`, `auth.*`, or `public.*`.
  3. Explicitly revoked execute permissions from public and granted only to `authenticated` and `service_role`:
     `REVOKE ALL ON FUNCTION ... FROM PUBLIC; GRANT EXECUTE ON FUNCTION ... TO authenticated, service_role;`.
- **Reference:** [Supabase database functions](https://supabase.com/docs/guides/database/functions).

## HHA-004 — MobSF analysis used a debug APK, not a release candidate

- **Severity:** Low (evidence gap; not a demonstrated release vulnerability)
- **Category:** Release artifact assurance
- **Status:** Open verification item.
- **Evidence:** Local MobSF static analysis ran on `app-debug.apk` (package `com.hellohealth`, version `1.2.0`, code 3). MobSF reported expected debug signing and debuggable configuration warnings, plus generic static warnings. These results cannot establish release artifact posture. `:app:assembleRelease` remains blocked by missing release signing configuration.
- **Required:** Produce a properly signed release candidate through the established secret-safe process, verify signing/debug flags, then rerun MobSF and retain a sanitized summary.

## HHA-005 — Anonymous callers can query account activity by UUID

- **Severity:** Low
- **Category:** Account enumeration / privacy
- **Status:** Open in source.
- **Affected component:** `public.is_active_account(text)` in `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql` and `run_sql_in_supabase.sql`.
- **Evidence:** The `SECURITY DEFINER` function returns whether an arbitrary UUID exists in `auth.users` and is absent from the tombstone table. It is explicitly granted to `anon`, making the status check callable without authentication through the exposed public RPC schema.
- **Impact:** A caller who knows or obtains a user UUID can test whether that account is active. UUID entropy limits brute-force discovery, but the grant is unnecessary for the RLS use case.
- **Required:** Revoke execute from `anon`; grant only to roles needed by RLS/server-side workflows and verify effective privileges in staging.

## Observations not promoted to findings

- Gitleaks full history scan found no matches. Current-tree heuristics (35) were confined to generated build output, IDE settings, and ignored local configuration; no confirmed committed credential was identified. No rotation is recommended from this scan alone.
- Trivy reported no filesystem vulnerabilities, misconfigurations, or secrets in scanned inputs. Dependency CVE coverage remains incomplete without lockfiles, verification metadata, or a release SBOM.
- Semgrep reported zero findings for the selected Java, Kotlin, security-audit, and secrets rules. The Android pack lookup returned HTTP 404, and this is not proof of safety.
- MobSF reported three high findings on the debug APK (debug signing, debuggable flag, debug configuration), 11 warnings, one informational finding, and a score of 49. Debug-only findings are not attributed to a release artifact. The raw report was not committed because it contains extracted strings and secret-like material.
- Room database encryption, Health Connect runtime permission behavior, deployed RLS/JWT configuration, privacy disclosures, and Play Console declarations need further investigation; see the checklist.
