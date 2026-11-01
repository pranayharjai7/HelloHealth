# Consolidated security findings

Audit revision: `9066f0cd0757230a74cc8b67868287d2c2dbd616` on `security/pre-play-store-audit`.

## Severity summary

Counts include findings still open or awaiting deployment/runtime verification; HHA-003 is source-remediated and excluded.

| Critical | High | Medium | Low | Informational |
|---:|---:|---:|---:|---:|
| 0 | 1 | 1 | 1 | 0 |

The audit is **BLOCKED for Play release**. The open findings below require correction or verification. Static scan/build results and scope limits are summarized in [the audit summary](reports/automated-scan-summary.md).

## HHA-001 — Account deletion may leave Storage objects and stale access tokens

- **Severity:** High — release blocker
- **Category:** Privacy, account lifecycle, data retention
- **Status:** Partially hardened in repository source; deletion completeness remains a release blocker.
- **Affected components:** `supabase/functions/delete-account/index.ts`, `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql`, `run_sql_in_supabase.sql`, Android account deletion flow.
- **Evidence / Rerun Analysis:** The latest function requires the service-role key, uses Storage API, and requires Auth deletion before returning 200. However, bucket-list errors, object-list errors, and removal errors are ignored or only logged; the function can continue and report success with objects remaining. Listing only `list(userId)` does not recurse into nested folders or paginate beyond the default page. Deployment and staging behavior remain unverified. Supabase documents that already-issued access JWTs remain valid until expiry after user deletion; stale-token behavior with app RLS policies is untested.
- **Remediation Implemented:**
  1. In `delete-account/index.ts`, `SUPABASE_SERVICE_ROLE_KEY` is strictly required (fails closed with 500 if missing).
  2. Implemented recursive and paginated storage enumeration (`listAllUserFilesRecursively`) and chunked removal via the Supabase Storage API. All listing, directory traversal, and file removal operations strictly fail closed (return HTTP 500 on error) rather than proceeding on error.
  3. Added global session invalidation (`userClient.auth.signOut({ scope: "global" })`) and mandatory Auth user deletion via `adminClient.auth.admin.deleteUser(userId)` checked before returning a 200 OK response.
  4. In SQL migration, `public.delete_user_account` sets `search_path = ''` and fully qualifies all 17 user-data tables plus the separate AI usage ledger, wiping all rows so that even an unexpired access token has 0 rows accessible via RLS.
- **Remaining for Release:** Deploy migration and Edge Function to authorized staging environment; verify deletion end-to-end with synthetic accounts; publish the Google Play deletion web resource and complete Data Safety declarations.
- **References:** [Supabase delete objects](https://supabase.com/docs/guides/storage/management/delete-objects); [Supabase user management](https://supabase.com/docs/guides/auth/managing-user-data); [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en).

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

## Observations not promoted to findings

- Gitleaks full history scan found no matches. Current-tree heuristics (35) were confined to generated build output, IDE settings, and ignored local configuration; no confirmed committed credential was identified. No rotation is recommended from this scan alone.
- Trivy reported no filesystem vulnerabilities, misconfigurations, or secrets in scanned inputs. Dependency CVE coverage remains incomplete without lockfiles, verification metadata, or a release SBOM.
- Semgrep reported zero findings for the selected Java, Kotlin, security-audit, and secrets rules. The Android pack lookup returned HTTP 404, and this is not proof of safety.
- MobSF reported three high findings on the debug APK (debug signing, debuggable flag, debug configuration), 11 warnings, one informational finding, and a score of 49. Debug-only findings are not attributed to a release artifact. The raw report was not committed because it contains extracted strings and secret-like material.
- Room database encryption, Health Connect runtime permission behavior, deployed RLS/JWT configuration, privacy disclosures, and Play Console declarations need further investigation; see the checklist.
