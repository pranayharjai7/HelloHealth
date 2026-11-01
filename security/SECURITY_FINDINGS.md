# Consolidated security findings

Audit revision: `32cf0145ebcc7bc40cf5175d0daf6493b71f4f17` on `security/pre-play-store-audit`.

## Severity summary

Counts include open source findings and release evidence gaps. HHA-003 and HHA-005 are remediated in source and excluded; deployed state remains unverified.

| Critical | High | Medium | Low | Informational |
|---:|---:|---:|---:|---:|
| 0 | 1 | 1 | 1 | 0 |

The audit is **BLOCKED for Play release**. The open findings below require correction or verification. Static scan/build results and scope limits are summarized in [the audit summary](reports/automated-scan-summary.md).

## HHA-001 — Duplicate global sign-out can stop account deletion before Auth identity removal

- **Severity:** High — release blocker
- **Category:** Privacy, account lifecycle, data retention
- **Status:** Open — high-severity release blocker. The latest source has a duplicate global sign-out sequence; staging behavior has not been exercised.
- **Affected components:** `supabase/functions/delete-account/index.ts`, `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql`, `run_sql_in_supabase.sql`, Android account deletion flow.
- **Evidence / Rerun Analysis:** In `supabase/functions/delete-account/index.ts`, the handler first sends `POST /auth/v1/logout?scope=global` with the caller JWT and requires a successful response. It then calls `adminClient.auth.admin.signOut(rawToken, "global")`, which the Supabase Auth SDK implements by sending another `POST /logout?scope=global` with the same JWT. Supabase Auth can return 403 `session_not_found` after a session has been revoked, and the admin SDK returns that error. The Edge Function then returns 500 before `adminClient.auth.admin.deleteUser(userId)`. By that point, the database tombstone/data cleanup and Storage object deletion have already run; these steps are not one transaction with Auth deletion. This is a source-based failure path; it was not exercised against a deployed Auth service.
- **Remediation Already Present:** `public.is_active_account` strictly fails closed (`COALESCE(v_exists, false)`); Storage policy DDL errors are no longer swallowed; `public.deleted_accounts` tombstones, active-account RLS policies on all 17 public tables, and mutation triggers on 12 text-keyed tables block stale-token data recreation. Storage enumeration/removal is recursive, paginated, and fail-closed. The remaining code issue is the duplicate sign-out before identity deletion.
- **Required Before Release:** Use one server-side global revocation operation, or make an already-revoked session explicitly idempotent, and ensure the handler can proceed to Auth identity deletion. Then test deletion and retry behavior with synthetic staging accounts, including stale JWT database/Storage access. Publish the Google Play deletion web resource and complete Data Safety declarations.
- **References:** [Supabase sign-out behavior](https://supabase.com/docs/guides/auth/signout); [Supabase GoTrueAdminApi.signOut implementation](https://github.com/supabase/supabase-js/blob/master/packages/core/auth-js/src/GoTrueAdminApi.ts); [Supabase 403 missing-session behavior](https://github.com/supabase/supabase-js/issues/1170); [Supabase delete objects](https://supabase.com/docs/guides/storage/management/delete-objects); [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en).

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
- **Status:** Remediated in repository source; migration deployment and effective privilege verification pending. Excluded from the open severity counts.
- **Affected component:** `public.is_active_account(text)` in `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql` and `run_sql_in_supabase.sql`.
- **Evidence / Rerun Analysis:** `public.is_active_account(text)` was previously granted to `anon`, allowing unauthenticated callers to query account activity by UUID. In the latest source revision, execute permissions were explicitly revoked from `PUBLIC` and `anon`:
  `REVOKE ALL ON FUNCTION public.is_active_account(text) FROM PUBLIC, anon;`
  `GRANT EXECUTE ON FUNCTION public.is_active_account(text) TO authenticated, service_role;`
  This restricts execution exclusively to authenticated users evaluating RLS policies and backend services.
- **Remediation Implemented:** Removed `anon` execute grant; restricted to `authenticated` and `service_role`.
- **Remaining for Release:** Verify effective privileges in staging.

## Observations not promoted to findings

- Gitleaks scanned 242 commits and reported one `generic-api-key` heuristic on descriptive text about the server-side Auth admin credential at `security/SECURITY_FINDINGS.md:31` in the audited commit; review confirmed there is no credential value in that text. The final current-tree scan reported 35 heuristics in ignored IDE settings, generated build output, or ignored local configuration. No confirmed credential was identified; no rotation is recommended from this scan alone.
- Trivy reported no filesystem vulnerabilities, misconfigurations, or secrets in scanned inputs. Dependency CVE coverage remains incomplete without lockfiles, verification metadata, or a release SBOM.
- Semgrep reported zero findings for the selected Java, Kotlin, security-audit, and secrets rules. The Android pack lookup returned HTTP 404, and this is not proof of safety.
- MobSF reported three high findings on the debug APK (debug signing, debuggable flag, debug configuration), 11 warnings, one informational finding, and a score of 49. Debug-only findings are not attributed to a release artifact. The raw report was not committed because it contains extracted strings and secret-like material.
- Room database encryption, Health Connect runtime permission behavior, deployed RLS/JWT configuration, privacy disclosures, and Play Console declarations need further investigation; see the checklist.
