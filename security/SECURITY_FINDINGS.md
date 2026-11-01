# Consolidated security findings

Audit revision: `69ddd1553ed2d109c72cd9da98fe6e6f3b4d54b1` on `security/pre-play-store-audit`.

## Severity summary

| Critical | High | Medium | Low | Informational |
|---:|---:|---:|---:|---:|
| 0 | 1 | 2 | 1 | 0 |

The audit is **BLOCKED for Play release**. The open findings below require correction or verification. Static scan/build results and scope limits are summarized in [the audit summary](reports/automated-scan-summary.md).

## HHA-001 — Account deletion does not reliably remove all user data or the Auth identity

- **Severity:** High — release blocker
- **Category:** Privacy, account lifecycle, data retention
- **Status:** Remediated and hardened in repository source; staging deployment and verification pending.
- **Affected components:** `supabase/functions/delete-account/index.ts`, `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql`, `run_sql_in_supabase.sql`, Android account deletion flow.
- **Evidence / Rerun Analysis:** The original implementation called direct SQL `DELETE FROM storage.objects` which does not delete stored bytes in S3, and allowed `delete-account` to return success without deleting the auth user if `SUPABASE_SERVICE_ROLE_KEY` was missing.
- **Remediation Implemented:**
  1. In `delete-account/index.ts`, `SUPABASE_SERVICE_ROLE_KEY` is now strictly required (fails closed with 500 if missing).
  2. Physical storage objects are deleted across all storage buckets via the Supabase Storage API (`adminClient.storage.from(bucket).remove(...)`), rather than direct SQL table manipulation.
  3. The Auth user deletion via `adminClient.auth.admin.deleteUser(userId)` is mandatory and checked before returning a 200 OK response.
  4. In SQL migration, `public.delete_user_account` now sets `search_path = ''` and fully qualifies all table names across all 17 public tables.
- **Remaining for Release:** Deploy migration and Edge Function to authorized staging environment; verify deletion end-to-end with synthetic accounts; publish the Google Play account deletion web resource and complete Data Safety declarations.
- **References:** [Supabase delete objects](https://supabase.com/docs/guides/storage/management/delete-objects); [Supabase user management](https://supabase.com/docs/guides/auth/managing-user-data); [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en).

## HHA-002 — AI coaching quota enforcement fails open and is raceable

- **Severity:** Medium
- **Category:** Backend API resource consumption / provider-cost abuse
- **Status:** Remediated and hardened in repository source; staging deployment and verification pending.
- **Affected components:** `supabase/functions/ai-coach/index.ts`, `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql`, `run_sql_in_supabase.sql`.
- **Evidence / Rerun Analysis:** Rerun review noted that RPC errors failed open, concurrent requests could race past quota counts before inserts, the request body was buffered completely via `req.text()` before checking size, and missing Supabase URL/key allowed unauthenticated calls.
- **Remediation Implemented:**
  1. Enforced mandatory fail-closed authentication and configuration in `ai-coach/index.ts` (returns 500 if Supabase config is missing; returns 401 if caller JWT is missing or invalid).
  2. Implemented a streaming byte reader `readBodyWithLimit` that enforces the 32 KB limit on the incoming byte stream before buffering the payload into memory, returning 413 immediately.
  3. Made quota enforcement strictly fail-closed: if `check_and_record_ai_usage` returns an error, the request is rejected with HTTP 500 rather than falling through unmetered. If quota is exceeded, returns 429 with `Retry-After`.
  4. Added transactional advisory locking in SQL (`PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(p_user_id))`) to serialize quota evaluations per user and eliminate the concurrency race condition.
- **Remaining for Release:** Deploy to authorized staging and verify with synthetic concurrent requests.
- **References:** [Supabase Edge Function authentication](https://supabase.com/docs/guides/functions/auth); [OWASP API4:2023](https://owasp.org/API-Security/editions/2023/en/0xa4-unrestricted-resource-consumption/).

## HHA-003 — SECURITY DEFINER functions use exposed schema and non-empty search paths

- **Severity:** Medium
- **Category:** Database function hardening
- **Status:** Remediated in repository source.
- **Affected components:** `public.delete_user_account(uuid)`, `public.check_and_record_ai_usage(text, integer, integer)`, `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql`, `run_sql_in_supabase.sql`.
- **Evidence / Rerun Analysis:** The functions set `search_path = public, auth`, exposing them to search path manipulation.
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
