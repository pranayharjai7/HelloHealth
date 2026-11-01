# Remediation roadmap

## Release blockers

1. **[REMEDIATED IN SOURCE — PENDING STAGING VERIFICATION] Implement account deletion (HHA-001).**
   - *Status:* In-app deletion confirmation flow added to `ProfileScreen.kt` and `AuthViewModel.kt`. Local Room database wipe, catalog re-seeding, sync worker cancellation, and session clearing added to `AuthRepositoryImpl.kt`. Database migration `20261101140000_account_deletion_and_ai_guardrails.sql` created with atomic stored procedure `public.delete_user_account` (`SECURITY DEFINER`) purging all 17 public tables and storage objects. Edge Function `supabase/functions/delete-account/index.ts` created to authenticate caller via JWT and delete Supabase Auth identity. Verified via 5 unit tests.
   - *Remaining for Release:* Deploy migration and Edge Function to authorized staging environment; verify deletion end-to-end with synthetic accounts; publish web deletion URL and complete Play Console Data Safety account-deletion declaration.
2. **Produce and inspect a signed release artifact.** Configure the existing release signing material through the established secure process; do not store signing secrets in the repository. Build the release APK/AAB, verify its signing/minification/debug flags, then run MobSF locally and archive a sanitized report.
3. **[RESOLVED — 0 ERRORS] Resolve lint errors before release.**
   - *Status:* All 7 original lint errors completely resolved (0 errors, 82 warnings). API 28 `ImageDecoder` in `EmotionCaptureScreen.kt` guarded with SDK check and `MediaStore` fallback for API 26–27. `ACTIVITY_RECOGNITION` permission added to `AndroidManifest.xml` for `WorkoutSessionService` (`foregroundServiceType="health"`).
4. **Complete Play health/privacy declarations.** Confirm every Health Connect permission is necessary and user-facing; complete the Health Apps declaration and Data Safety form; disclose the AI health-context flow, processors, retention, and deletion behavior in the user-facing privacy materials.

## High-priority fixes

1. **Verify deployed Supabase controls.** In an authorized staging project, confirm RLS is enabled on all user tables, owner policies match the checked-in SQL, JWT verification is active for `ai-coach`, and no service-role key is exposed to the app.
2. **[REMEDIATED IN SOURCE — PENDING STAGING VERIFICATION] Close AI resource-abuse gaps (HHA-002).**
   - *Status:* Implemented server-side controls in `supabase/functions/ai-coach/index.ts` (32 KB payload limit, JWT verification, max 10 messages, max 2000 chars/message, max 8000 chars total, max 2000 chars system prompt, temperature clamped [0.0, 1.0], maxOutputTokens clamped [100, 2048], sanitized logging). Created `public.ai_usage_ledger` and atomic stored procedure `public.check_and_record_ai_usage` enforcing 5 req/min burst and 30 req/day quota limits. Verified via 5 unit tests in `CoachingProxyDataSourceTest.kt`.
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
