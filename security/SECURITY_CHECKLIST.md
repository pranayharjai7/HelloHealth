# Security checklist

Status meanings: **PASSED** means the reviewed source or executed check supports the control within the stated scope; **FAILED** means an identified requirement is unmet; **NEEDS INVESTIGATION** means the source is incomplete evidence or the answer depends on deployment/runtime context; **NOT TESTED** means no relevant check was performed; **NOT APPLICABLE** means the control does not apply to this Android-only repository.

| MASVS group | Status | HelloHealth evidence and limits |
|---|---|---|
| STORAGE | NEEDS INVESTIGATION | Room database is in app-private internal storage (`DatabaseModule`); backup is disabled in the manifest and extraction rules. No explicit SQLCipher/app-layer encryption was identified. OWASP MASTG allows unencrypted private-sandbox storage for L1; stronger L2 encryption depends on the chosen threat profile. Runtime storage/log inspection was not run. |
| CRYPTO | NEEDS INVESTIGATION | No custom cryptographic implementation was established as a defect by the source/scanner review. Token/key handling inside runtime SDKs, device state, and cryptographic behavior were not dynamically tested. |
| AUTH | NEEDS INVESTIGATION | Email and Google sign-in/sign-out are present. Source SQL defines owner-scoped RLS for 17 user tables with tombstone checking and fail-closed active-account verification (`is_active_account`, revoked from `anon`). The Storage restrictive policy is installed fail-closed. Edge Function `delete-account` currently sends two global sign-out requests with the same JWT; the second may fail after the first revokes the session, returning 500 before Auth identity deletion. Staging verification of deployed policies and session lifecycle remains pending. See HHA-001. |
| NETWORK | NEEDS INVESTIGATION | No cleartext override or custom trust-all configuration was found in the reviewed manifest/source. Runtime TLS, endpoint identity validation, redirects, and request/response data were not captured. Certificate pinning is not configured; its absence alone is not treated as a defect. |
| PLATFORM | PASSED (static), NOT TESTED (runtime) | Launcher/rationale activity is exported; workout service and startup provider are not exported. Static component review found no exposed service. Intent/deep-link fuzzing and sensitive-screen capture tests were not run. |
| CODE | PASSED (lint/tests); NEEDS INVESTIGATION (supply chain) | `assembleDebug` succeeded; `testDebugUnitTest` passed 575 tests (100% pass rate). `lintDebug` passed with 0 errors and 82 warnings (original seven lint errors resolved). No Gradle lockfiles or dependency verification metadata were found, limiting CVE/SCA coverage. |
| RESILIENCE | NEEDS INVESTIGATION | Release minification and resource shrinking are enabled. No signed release APK/AAB was produced because the local release signing configuration is absent. MobSF scanned a debug APK only; debug signing/debuggable warnings are expected for that artifact and do not establish release posture. Runtime tamper/root tests were not performed. |
| PRIVACY | FAILED / RELEASE BLOCKER | Storage enumeration/deletion is recursive, paginated, and fail-closed in source. Stale-token controls are fail-closed across all 17 public tables, mutation triggers protect 12 text-keyed tables, and Storage restrictive policy installation is fail-closed. The duplicate sign-out sequence can stop the deletion handler before Auth identity removal. AI coaching source controls fail closed and serialize quota checks. Staging deployment/verification and Google Play deletion web resource/declarations remain pending. See HHA-001 and HHA-002. |
| iOS-specific controls | NOT APPLICABLE | This repository contains an Android application. |

## Additional release checks

| Check | Status | Evidence |
|---|---|---|
| Gitleaks Git history | PASSED (one false positive) | 242 commits scanned; one generic-api-key heuristic matched descriptive audit prose about the server-side Auth admin credential; the text contains no credential value. |
| Gitleaks current filesystem | NEEDS INVESTIGATION (scanner heuristics) | 35 heuristic matches in ignored IDE configuration, generated build outputs, or ignored local configuration. Values were redacted; none was confirmed as a credential. |
| Trivy filesystem | PASSED within available inputs | 0 vulnerability, misconfiguration, or secret findings. Dependency analysis is incomplete without lockfiles/SBOM. |
| Semgrep source | PASSED within selected rules | 0 findings with `p/java`, `p/kotlin`, `p/security-audit`, and `p/secrets`; the requested `p/android` registry pack returned HTTP 404. |
| MobSF APK/AAB analysis | PARTIAL / NEEDS INVESTIGATION | Local MobSF scan completed on `app-debug.apk`, not a release artifact. Reported debug certificate/debuggable findings are expected for debug; raw output is not committed because it contains extracted strings/secrets. Release signing configuration is still unavailable. |
| OWASP ZAP backend/API test | NOT TESTED | No authorized staging target was supplied; production was not tested. ZAP installation also required administrator elevation. |
| Release artifact | FAILED / BLOCKED | `:app:assembleRelease` refused to create an unsigned artifact because release signing configuration is unavailable. |
| Instrumented/device tests | NOT TESTED | A physical device was connected; no install or wireless ADB change was made. |
| Play Console declarations | NOT TESTED | No Play Console access or submitted privacy/Data Safety/Health Apps declarations were in scope. |
