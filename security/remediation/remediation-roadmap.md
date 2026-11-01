# Remediation roadmap

## Release blockers

1. **[OPEN — RELEASE BLOCKER] Complete and verify account deletion (HHA-001).**
   - *Status:* In-app deletion confirmation flow added to `ProfileScreen.kt` and `AuthViewModel.kt`. Local Room database wipe, catalog re-seeding, sync worker cancellation, and session clearing added to `AuthRepositoryImpl.kt`. Database migration `20261101140000_account_deletion_and_ai_guardrails.sql` includes `public.delete_user_account` (`SECURITY DEFINER`, `SET search_path = ''`, schema-qualified references) purging 17 user-data tables plus the AI usage ledger. Edge Function `supabase/functions/delete-account/index.ts` requires caller JWT and service-role configuration and uses the Storage API before Auth deletion. Latest source recursively paginates user-prefixed Storage paths and fails closed on Storage errors. A remaining blocker is stale access JWTs: owner RLS policies can accept the token and allow new writes until expiry. Confirm all deployed user-owned objects use the UUID prefix.
   - *Remaining for Release:* Test stale-token/RLS behavior and prevent post-deletion writes until outstanding JWTs expire; verify deployed object path conventions and test with synthetic staging accounts; publish web deletion URL and complete Play Console account-deletion declaration.
2. **Produce and inspect a signed release artifact.** Configure the existing release signing material through the established secure process; do not store signing secrets in the repository. Build the release APK/AAB, verify its signing/minification/debug flags, then run MobSF locally and archive a sanitized report.
3. **[RESOLVED — 0 ERRORS] Resolve lint errors before release.**
   - *Status:* All 7 original lint errors completely resolved (0 errors, 82 warnings). API 28 `ImageDecoder` in `EmotionCaptureScreen.kt` guarded with SDK check and `MediaStore` fallback for API 26–27. `ACTIVITY_RECOGNITION` permission added to `AndroidManifest.xml` for `WorkoutSessionService` (`foregroundServiceType="health"`).
4. **Complete Play health/privacy declarations.** Confirm every Health Connect permission is necessary and user-facing; complete the Health Apps declaration and Data Safety form; disclose the AI health-context flow, processors, retention, and deletion behavior in the user-facing privacy materials.

## High-priority fixes

1. **Verify deployed Supabase controls.** In an authorized staging project, confirm RLS is enabled on all user tables, owner policies match the checked-in SQL, JWT verification is active for `ai-coach`, and no service-role key is exposed to the app.
2. **[REMEDIATED IN SOURCE — PENDING STAGING VERIFICATION] Make AI resource-abuse controls reliable (HHA-002 & HHA-003).**
   - *Status:* Implemented server-side controls in `supabase/functions/ai-coach/index.ts`: fail-closed configuration and JWT checks, streaming body limit (`readBodyWithLimit`) rejecting bodies > 32 KB before buffering, max 10 messages, max 2000 chars/message, max 8000 chars total, max 2000 chars system prompt, temperature clamped [0.0, 1.0], maxOutputTokens clamped [100, 2048], and sanitized logging. In `public.check_and_record_ai_usage`: added `SET search_path = ''` with schema-qualified references, transactional advisory locking (`pg_catalog.pg_advisory_xact_lock`) to serialize quota evaluations per user and eliminate concurrency races, and fail-closed error handling in Edge Function. Client fallback behavior has unit tests, but those tests do not exercise the Edge Function or SQL quota implementation. No backend runtime tests were performed.
   - *Remaining for Release:* Deploy to authorized staging and verify rate limiting and quota enforcement with synthetic concurrent requests.
3. **Complete privacy data-flow review.** Capture network traffic from a synthetic account through each health/AI feature and reconcile actual fields/providers/retention with disclosures. Do not use production user data for this test.

## Medium-priority fixes

1. **Choose a storage assurance profile.** Document the app threat model and whether MASVS L1 or L2 is the target. If L2 is required, protect the Room database with a maintained encryption design and device-keystore-managed keys; test migration/recovery behavior.
2. **Improve dependency evidence.** Add Gradle dependency locking and verification metadata or an SBOM process; then run a dependency-aware CVE scan against the resolved release graph.
3. **Review logging and API key restrictions.** Confirm release logs do not include health data or tokens; restrict the Android Maps key by application ID, signing certificate, and required APIs.

## Later release improvements

1. Add an authorized staging ZAP baseline/API test to CI, with test accounts and non-production data.
2. Add a dedicated emulator-based instrumented test job for auth, data deletion, permissions, Health Connect flows, and backup behavior.
3. Consider MobSF static analysis for every signed release candidate and retain sanitized evidence by commit SHA.
4. Revisit static-only resilience controls against the chosen threat profile; test runtime behavior before making claims about tamper resistance.


## Latest rerun follow-up

- HHA-001 remains open: Storage cleanup is now recursive, paginated, and fail-closed in source. Verify all owned objects are under the UUID prefix and prevent stale-token writes through RLS/session checks; test in staging.
- HHA-002 and HHA-003 fixes are present in source; deploy the migration/functions and verify quota concurrency, body-size rejection, and function grants in staging.
- Deno and Supabase CLI were unavailable, and no Supabase project access or authorized staging endpoint was provided.
