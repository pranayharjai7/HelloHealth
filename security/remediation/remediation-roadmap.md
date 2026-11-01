# Remediation roadmap

## Release blockers

1. **Implement account deletion (HHA-001).** Add an in-app deletion request and required web deletion resource. Implement a backend workflow that deletes the Auth identity and all associated health/profile data, storage objects, and processor-linked data as applicable. Test end-to-end in staging with synthetic records. Confirm the Play Console Data Safety and account-deletion entries.
2. **Produce and inspect a signed release artifact.** Configure the existing release signing material through the established secure process; do not store signing secrets in the repository. Build the release APK/AAB, verify its signing/minification/debug flags, then run MobSF locally and archive a sanitized report.
3. **Resolve lint errors before release.** Guard or replace the API 28 `ImageDecoder` calls for minSdk 26–27; resolve the health foreground-service permission requirement based on the actual feature. Re-run lint after the authorized code change.
4. **Complete Play health/privacy declarations.** Confirm every Health Connect permission is necessary and user-facing; complete the Health Apps declaration and Data Safety form; disclose the AI health-context flow, processors, retention, and deletion behavior in the user-facing privacy materials.

## High-priority fixes

1. **Verify deployed Supabase controls.** In an authorized staging project, confirm RLS is enabled on all user tables, owner policies match the checked-in SQL, JWT verification is active for `ai-coach`, and no service-role key is exposed to the app.
2. **Close AI resource-abuse gaps (HHA-002).** Add server-side input and message limits, clamp output-token settings, enforce per-user/global quotas and rate limits, and add usage alerts. Verify with staging boundary/abuse tests.
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
