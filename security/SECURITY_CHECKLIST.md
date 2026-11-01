# Security checklist

Status meanings: **PASSED** means the reviewed source or executed check supports the control within the stated scope; **FAILED** means an identified requirement is unmet; **NEEDS INVESTIGATION** means the source is incomplete evidence or the answer depends on deployment/runtime context; **NOT TESTED** means no relevant check was performed; **NOT APPLICABLE** means the control does not apply to this Android-only repository.

| MASVS group | Status | HelloHealth evidence and limits |
|---|---|---|
| STORAGE | NEEDS INVESTIGATION | Room database is in app-private internal storage (`DatabaseModule`); backup is disabled in the manifest and extraction rules. No explicit SQLCipher/app-layer encryption was identified. OWASP MASTG allows unencrypted private-sandbox storage for L1; stronger L2 encryption depends on the chosen threat profile. Runtime storage/log inspection was not run. |
| CRYPTO | NEEDS INVESTIGATION | No custom cryptographic implementation was established as a defect by the source/scanner review. Token/key handling inside runtime SDKs, device state, and cryptographic behavior were not dynamically tested. |
| AUTH | NEEDS INVESTIGATION | Email and Google sign-in/sign-out are present. Source SQL defines owner-scoped RLS for 17 tables, but deployed policies, JWT enforcement, session lifecycle, and cross-account access were not dynamically tested in staging. Account deletion has been implemented in source (in-app Danger Zone confirmation dialog, ViewModel, local database wipe & sync cancellation in repository, atomic stored procedure `public.delete_user_account` purging all 17 tables and storage, and `delete-account` Edge Function deleting auth identity); staging verification is pending. See HHA-001. |
| NETWORK | NEEDS INVESTIGATION | No cleartext override or custom trust-all configuration was found in the reviewed manifest/source. Runtime TLS, endpoint identity validation, redirects, and request/response data were not captured. Certificate pinning is not configured; its absence alone is not treated as a defect. |
| PLATFORM | PASSED (static), NOT TESTED (runtime) | Launcher/rationale activity is exported; workout service and startup provider are not exported. Static component review found no exposed service. Intent/deep-link fuzzing and sensitive-screen capture tests were not run. |
| CODE | PASSED (lint/tests); NEEDS INVESTIGATION (supply chain) | `assembleDebug` succeeded; `testDebugUnitTest` passed 575 tests (100% pass rate). `lintDebug` passed with 0 errors and 82 warnings (all 7 original lint errors resolved: API 28 `ImageDecoder` guarded with API 26–27 fallback in `EmotionCaptureScreen.kt`; `ACTIVITY_RECOGNITION` permission added for health foreground-service in `AndroidManifest.xml`). No Gradle lockfiles or dependency verification metadata were found, limiting CVE/SCA coverage. |
| RESILIENCE | NEEDS INVESTIGATION | Release minification and resource shrinking are enabled. No signed release APK/AAB was produced because the local release keystore is absent. MobSF artifact analysis, signing verification, debuggability inspection of a release artifact, and runtime tamper/root tests were not performed. |
| PRIVACY | IN PROGRESS / REMEDIATED IN SOURCE | In-app account deletion request flow implemented with two-step confirmation dialog; complete server-side database cleanup implemented with `delete_user_account(target_user_id)` procedure purging all 17 public tables and storage objects, and `delete-account` Edge Function. AI coaching abuse protections and input/token caps implemented (HHA-002). End-to-end staging validation and Play Console Data Safety / Account Deletion web resource declarations remain to be finalized. |
| iOS-specific controls | NOT APPLICABLE | This repository contains an Android application. |

## Additional release checks

| Check | Status | Evidence |
|---|---|---|
| Gitleaks Git history | PASSED (no confirmed findings) | Full history scan on the audited revision returned 0 findings. |
| Gitleaks current filesystem | NEEDS INVESTIGATION (scanner heuristics) | 36 matches were limited to ignored IDE configuration, generated build outputs, and ignored local configuration; values were redacted and none was confirmed as a committed credential. |
| Trivy filesystem | PASSED within available inputs | 0 vulnerability, misconfiguration, or secret findings. Dependency analysis is incomplete without lockfiles/SBOM. |
| Semgrep source | PASSED within selected rules | 0 findings with `p/java`, `p/kotlin`, `p/security-audit`, and `p/secrets`; the requested `p/android` registry pack returned HTTP 404. |
| MobSF APK/AAB analysis | NOT TESTED | Docker Engine API returned an error; no release artifact was available. |
| OWASP ZAP backend/API test | NOT TESTED | No authorized staging target was supplied; production was not tested. ZAP installation also required administrator elevation. |
| Release artifact | FAILED / BLOCKED | `:app:assembleRelease` refused to create an unsigned artifact because no release keystore is configured. |
| Instrumented/device tests | NOT TESTED | A physical device was connected; no install or wireless ADB change was made. |
| Play Console declarations | NOT TESTED | No Play Console access or submitted privacy/Data Safety/Health Apps declarations were in scope. |
