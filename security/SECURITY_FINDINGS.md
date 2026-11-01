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
- **Status:** Remediated in repository source; staging deployment and runtime verification pending.
- **Affected components:** `supabase/functions/delete-account/index.ts`, `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql`, `run_sql_in_supabase.sql`, Android account deletion flow.
- **Evidence / Rerun Analysis:** Global sign-out revokes refresh tokens on the GoTrue server, but already-issued access JWTs remain cryptographically valid until their expiry timestamp (~1 hour). In previous revisions, PostgREST evaluated `user_id = auth.uid()` statelessly without checking `auth.users`, allowing a stale JWT to recreate rows on the 12 text-keyed tables where foreign keys were absent. Previous iterations also had gaps: `is_active_account` caught errors and set `v_exists := true` (failing open), Storage policy DDL swallowed errors in an exception block, and the Edge Function's sessionless `userClient.auth.signOut()` returned without dispatching a network revocation call. In the latest source revision, all issues are remediated and strictly fail-closed:
  1. Edge Function `delete-account/index.ts` explicitly revokes server refresh tokens by dispatching a direct HTTP request to GoTrue `POST /auth/v1/logout?scope=global` using the caller's JWT bearer token, checking for HTTP 2xx and returning 500 on any failure, followed by admin SDK revocation (`adminClient.auth.admin.signOut(rawToken, "global")`).
  2. `public.is_active_account` strictly fails closed: the lookup evaluates `RETURN COALESCE(v_exists, false);` without catching errors or setting `v_exists := true`.
  3. The Storage restrictive policy (`storage_prevent_deleted_account_mutation` on `storage.objects`) is installed directly without `EXCEPTION WHEN OTHERS THEN NULL`. Any DDL failure halts the migration, guaranteeing the safeguard cannot be silently bypassed.
  4. Tombstone table `public.deleted_accounts` is recorded at the start of `public.delete_user_account`, and all 17 public tables enforce active-account RLS policies while 12 text-keyed tables enforce `BEFORE INSERT OR UPDATE` triggers.
- **Remediation Implemented:**
  1. Direct server-side Auth revocation via GoTrue `POST /auth/v1/logout?scope=global` and `adminClient.auth.admin.signOut` with caller JWT (fail closed on non-2xx).
  2. `public.is_active_account` made strictly fail-closed (`COALESCE(v_exists, false)`).
  3. Storage restrictive policy installed without swallowing DDL errors.
  4. Tombstone table `public.deleted_accounts`, 17 active-account RLS policies, and 12 mutation triggers prevent stale-token data recreation.
  5. Mandatory service-role key, recursive/paginated Storage API enumeration and chunked removal, and hard Auth identity deletion before 200 OK.
- **Remaining for Release:** Verify stale-token rejection across Postgres and Storage with synthetic staging accounts; publish the Google Play deletion web resource and complete Data Safety declarations.
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
- **Status:** Remediated in repository source; migration deployment pending.
- **Affected component:** `public.is_active_account(text)` in `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql` and `run_sql_in_supabase.sql`.
- **Evidence / Rerun Analysis:** `public.is_active_account(text)` was previously granted to `anon`, allowing unauthenticated callers to query account activity by UUID. In the latest source revision, execute permissions were explicitly revoked from `PUBLIC` and `anon`:
  `REVOKE ALL ON FUNCTION public.is_active_account(text) FROM PUBLIC, anon;`
  `GRANT EXECUTE ON FUNCTION public.is_active_account(text) TO authenticated, service_role;`
  This restricts execution exclusively to authenticated users evaluating RLS policies and backend services.
- **Remediation Implemented:** Removed `anon` execute grant; restricted to `authenticated` and `service_role`.
- **Remaining for Release:** Verify effective privileges in staging.

## Observations not promoted to findings

- Gitleaks full history scan found no matches. Current-tree heuristics (35) were confined to generated build output, IDE settings, and ignored local configuration; no confirmed committed credential was identified. No rotation is recommended from this scan alone.
- Trivy reported no filesystem vulnerabilities, misconfigurations, or secrets in scanned inputs. Dependency CVE coverage remains incomplete without lockfiles, verification metadata, or a release SBOM.
- Semgrep reported zero findings for the selected Java, Kotlin, security-audit, and secrets rules. The Android pack lookup returned HTTP 404, and this is not proof of safety.
- MobSF reported three high findings on the debug APK (debug signing, debuggable flag, debug configuration), 11 warnings, one informational finding, and a score of 49. Debug-only findings are not attributed to a release artifact. The raw report was not committed because it contains extracted strings and secret-like material.
- Room database encryption, Health Connect runtime permission behavior, deployed RLS/JWT configuration, privacy disclosures, and Play Console declarations need further investigation; see the checklist.
