# Consolidated security findings

Audit revision: `543c67e0becfaee7a28c704246ce041600dbea3d` on `security/pre-play-store-audit`.

## Severity summary

Counts include findings still open or awaiting deployment/runtime verification; HHA-003 is source-remediated and excluded.

| Critical | High | Medium | Low | Informational |
|---:|---:|---:|---:|---:|
| 0 | 1 | 1 | 1 | 0 |

The audit is **BLOCKED for Play release**. The open findings below require correction or verification. Static scan/build results and scope limits are summarized in [the audit summary](reports/automated-scan-summary.md).

## HHA-001 — Stale access tokens can recreate data after account deletion

- **Severity:** High — release blocker
- **Category:** Privacy, account lifecycle, data retention
- **Status:** Remediated in repository source; staging deployment and runtime verification pending.
- **Affected components:** `supabase/functions/delete-account/index.ts`, `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql`, `run_sql_in_supabase.sql`, Android account deletion flow.
- **Evidence / Rerun Analysis:** Global sign-out revokes refresh tokens, but already-issued access JWTs remain cryptographically valid until their expiry timestamp (~1 hour). In previous revisions, PostgREST evaluated `user_id = auth.uid()` statelessly without checking `auth.users`, allowing a stale JWT to recreate rows on the 12 text-keyed tables (`emotion_records`, `workout_plans`, `workout_days`, `planned_exercises`, `vitals_samples`, `body_metrics`, `nutrition_entries`, `workout_sessions`, `session_sets`, `streaks`, `points_ledger`, `achievements`) where foreign keys to `auth.users(id)` were absent. In addition, `userClient.auth.signOut({ scope: "global" })` previously caught and ignored returned errors. In the latest source revision, all gaps have been remediated:
  1. Edge Function `delete-account/index.ts` explicitly checks `{ error: signOutError }` from `userClient.auth.signOut({ scope: "global" })` and fails closed (HTTP 500) if session revocation fails.
  2. Created tombstone table `public.deleted_accounts (user_id text PRIMARY KEY, deleted_at timestamptz)` with RLS enabled and revoked from public/anon/authenticated.
  3. `public.delete_user_account` inserts the target user ID into `public.deleted_accounts` at the very beginning of the purge transaction.
  4. Implemented fast indexed function `public.is_active_account(p_user_id text) RETURNS boolean` (`STABLE SECURITY DEFINER SET search_path = ''`) which checks that `p_user_id` is NOT in `public.deleted_accounts` AND exists in `auth.users`.
  5. Hardened RLS policies across all 17 public tables (`profiles`, `activity_goals`, `food_preferences`, `daily_health_snapshots`, `nutrition_goals`, `emotion_records`, `workout_plans`, `workout_days`, `planned_exercises`, `vitals_samples`, `body_metrics`, `nutrition_entries`, `workout_sessions`, `session_sets`, `streaks`, `points_ledger`, `achievements`) to require `AND public.is_active_account(auth.uid()::text)` for both `USING` and `WITH CHECK`.
  6. Added defense-in-depth trigger `trg_prevent_deleted_mutation` `BEFORE INSERT OR UPDATE` on all 12 text-keyed tables calling `public.prevent_deleted_account_mutation()` which raises an exception on any mutation attempt for inactive or deleted accounts.
  7. Hardened `public.check_and_record_ai_usage` to reject AI requests from deleted/inactive accounts.
  8. Added a restrictive RLS policy on `storage.objects` blocking all uploads/mutations from deleted/inactive accounts across all storage buckets.
- **Remediation Implemented:**
  1. In `delete-account/index.ts`, `SUPABASE_SERVICE_ROLE_KEY` is strictly required (fails closed with 500 if missing).
  2. Implemented recursive and paginated storage enumeration (`listAllUserFilesRecursively`) and chunked removal via the Supabase Storage API, failing closed on any storage error.
  3. Enforced fail-closed session sign-out error checking on `userClient.auth.signOut({ scope: "global" })` and mandatory Auth user deletion via `adminClient.auth.admin.deleteUser(userId)` verified before returning 200 OK.
  4. Created `public.deleted_accounts` tombstone table and `public.is_active_account(p_user_id text)` verification function.
  5. Hardened RLS policies on all 17 public database tables with active-account verification.
  6. Added `BEFORE INSERT OR UPDATE` mutation triggers on all 12 text-keyed tables.
  7. Added restrictive storage policy on `storage.objects` to block post-deletion uploads across all buckets.
  8. In SQL migration, all procedures set `search_path = ''` with schema-qualified references.
- **Remaining for Release:** Deploy migration and Edge Function to authorized staging; verify end-to-end rejection of stale access JWTs with an unexpired synthetic token; publish the Google Play deletion web resource and complete Data Safety declarations.
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
