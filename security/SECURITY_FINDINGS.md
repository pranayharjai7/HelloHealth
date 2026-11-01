# Consolidated security findings

Audit revision: `15171845ecf72eaea1dddfa5eb248b43652fa567` on `security/pre-play-store-audit`.

## Severity summary

| Critical | High | Medium | Low | Informational |
|---:|---:|---:|---:|---:|
| 0 | 1 | 1 | 0 | 0 |

Build/lint failures and scanner limitations are recorded in the checklist and build/tool reports. They are not counted as security vulnerabilities.

## HHA-001 — No account deletion request flow; user data is not consistently linked for deletion

- **Severity:** High — release blocker
- **Category:** Privacy, account lifecycle, data retention
- **Affected components:** `app/src/main/java/com/hellohealth/domain/repository/AuthRepository.kt`, `app/src/main/java/com/hellohealth/data/repository/AuthRepositoryImpl.kt`, `app/src/main/java/com/hellohealth/ui/profile/ProfileScreen.kt`, `supabase/functions/delete-account/index.ts`, `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql`, `run_sql_in_supabase.sql`
- **Status:** Remediated in repository source; unit tests verified; staging deployment and end-to-end integration verification pending.
- **Evidence / reproduction:** The auth contract and implementation originally provided sign-up, sign-in, Google sign-in, sign-out, and profile update operations, but no account-deletion operation or in-app deletion request was found. In `run_sql_in_supabase.sql`, only a subset of user tables had an Auth foreign key with `ON DELETE CASCADE`; multiple health-related tables used `user_id text` without an Auth foreign key, and `nutrition_goals` had an Auth foreign key without cascade.
- **Impact:** Users could not initiate account deletion from the app. If an account was deleted separately, health and profile-related rows could remain or prevent deletion. Google Play requires apps that allow account creation to provide an in-app deletion request path and a web resource for deletion. Health information could remain associated with an account after the user expected removal.
- **Implemented remediation:**
  1. **Database Migration:** Created migration `20261101140000_account_deletion_and_ai_guardrails.sql` (and appended to `run_sql_in_supabase.sql`). Fixed `nutrition_goals` foreign key to `ON DELETE CASCADE`. Implemented atomic `public.delete_user_account(target_user_id uuid)` stored procedure (`SECURITY DEFINER`) that explicitly purges user records from all 17 public tables (`profiles`, `activity_goals`, `food_preferences`, `daily_health_snapshots`, `nutrition_goals`, `emotion_records`, `workout_plans`, `workout_days`, `planned_exercises`, `vitals_samples`, `body_metrics`, `nutrition_entries`, `workout_sessions`, `session_sets`, `streaks`, `points_ledger`, `achievements`, and `ai_usage_ledger`) and removes user objects from `storage.objects`.
  2. **Edge Function:** Created `supabase/functions/delete-account/index.ts` which verifies caller JWT via `auth.getUser()`, rejects unauthenticated calls (401), executes `delete_user_account(userId)` RPC, and permanently deletes auth identity via `adminClient.auth.admin.deleteUser(userId)`.
  3. **Android Client UI & State:** Added "Account" Danger Zone card in `ProfileScreen.kt` with "Delete Account" button and two-step confirmation `AlertDialog`. Hooked up `AuthViewModel.deleteAccount(onSuccess)` to navigate back to `Screen.Login.route` clearing the backstack.
  4. **Repository & Local Database Wipe:** Added `AuthRepository.deleteAccount()` in `AuthRepositoryImpl.kt` which invokes the `delete-account` Edge Function, cancels background sync (`SyncScheduler.cancelAll()`), wipes local Room database (`database.clearAllTables()`), re-seeds exercise/nutrition catalog caches, clears session, and logs out.
- **Fix verification:** Verified with 5 unit tests (2 in `AuthViewModelTest` and 3 in `AuthRepositoryImplTest`) covering unauthenticated call rejection, server error handling, sync worker cancellation, Room database purging, and UI error surfacing. Staging deployment verification with synthetic accounts is required before production release.
- **OWASP mapping:** MASVS-PRIVACY-4; MASWE-0076; CWE-359 per the MASWE mapping.
- **Tool/source:** Manual review of the auth repository/UI and SQL schema; [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en); [MASVS-PRIVACY-4](https://mas.owasp.org/MASVS/controls/MASVS-PRIVACY-4/); [MASWE-0076](https://mas.owasp.org/MASWE/MASVS-PRIVACY/MASWE-0076/).

## HHA-002 — AI coaching requests have no source-level size, count, or per-user rate limits

- **Severity:** Medium
- **Category:** Backend API resource consumption / provider-cost abuse
- **Affected component:** `supabase/functions/ai-coach/index.ts`, `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql`
- **Status:** Remediated in repository source; unit tests verified; staging deployment and end-to-end integration verification pending.
- **Evidence / reproduction:** The original handler checked only that `messages` was a non-empty array. It passed user-supplied message text, message count, `temperature`, and `maxOutputTokens` to Gemini/OpenRouter; the token budget was not clamped in the function. No per-user rate/usage limit was visible in the function.
- **Impact:** An authenticated user who could call the function directly could submit oversized/repeated requests or a very high output-token budget, increasing provider cost and consuming shared quota.
- **Implemented remediation:**
  1. **Atomic Rate-Limiting & Quota Ledger:** Created `public.ai_usage_ledger` table with RLS and `public.check_and_record_ai_usage(p_user_id, p_max_requests_per_minute, p_max_requests_per_day)` stored procedure (`SECURITY DEFINER`) in migration `20261101140000_account_deletion_and_ai_guardrails.sql`. Enforces max 5 requests/minute (burst protection) and max 30 requests/day (daily budget protection) atomically across serverless function instances.
  2. **Edge Function Server-side Guardrails:** In `supabase/functions/ai-coach/index.ts`:
     - Payload size limit: checks `Content-Length` and raw request text to reject bodies > 32 KB (HTTP 413 Payload Too Large).
     - Authentication gate: verifies user JWT with `auth.getUser()`, returning HTTP 401 if invalid or absent.
     - Rate-limit gate: calls `check_and_record_ai_usage` via Supabase admin RPC; returns HTTP 429 with `Retry-After: 60` and descriptive error code on violation.
     - Input message count: capped at max 10 messages (HTTP 400).
     - Text length caps: individual messages capped at max 2,000 characters; total conversation context capped at max 8,000 characters; system prompt capped at max 2,000 characters.
     - Parameter clamping: `temperature` clamped to `[0.0, 1.0]`; `maxOutputTokens` clamped to `[100, 2048]`.
     - Sanitized logging: logs metadata and character counts only; does not log message text or sensitive health information.
  3. **Android Client Fallback & Robustness:** `CoachingProxyDataSource.kt` handles HTTP 400, 413, 429, and 502 gracefully, falling back to local rule-based coaching without throwing or crashing.
- **Fix verification:** Verified with 5 unit tests in `CoachingProxyDataSourceTest.kt` confirming graceful mapping of 200 (Success), 429 (Failure), 413 (Failure), 400 (Failure), and 502 (Failure).
- **OWASP mapping:** Backend function, outside MASVS/MASTG mobile-app test scope; OWASP API Security Top 10 API4:2023 (Unrestricted Resource Consumption); CWE-400.
- **Tool/source:** Manual review of `supabase/functions/ai-coach/index.ts`; no ZAP or live test was performed.

## Observations not promoted to security findings

- **Room database encryption:** `DatabaseModule` builds `hello_health.db` with ordinary Room in app-private storage. No explicit database encryption was found. OWASP MASTG notes unencrypted private-sandbox storage can meet L1; L2 requires stronger encryption. Choose a target profile and threat model, then dynamically inspect stored health data. This is not counted as a defect on current evidence. References: MASVS-STORAGE-1 and MASTG-TEST-0304/0306.
- **Third-party health-data processing:** When AI coaching is enabled, the app sends selected daily health context through its Edge Function to Gemini/OpenRouter. Verify privacy policy, user disclosures/consent, Data Safety answers, vendor retention, and deletion coverage before release. Runtime data-flow and Play Console declarations were not available.
- **Health permissions:** The manifest declares broad Health Connect read permissions. Confirm each permission is tied to a user-facing feature and complete the Play Health Apps declaration.
- **Client-side Maps key:** A Maps key is present as an Android client configuration value. It is not automatically a server secret; verify package/signing-certificate and API restrictions in the owning cloud project.
- **Gitleaks current-tree matches:** Scanner heuristics were limited to ignored local IDE/config/build files. Values were redacted. No current match was confirmed as a Git-history leak; no rotation is recommended from this scan alone.
