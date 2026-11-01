# Security checklist

Status meanings: **PASSED** means the reviewed source or executed check supports the control within the stated scope; **FAILED** means an identified requirement is unmet; **NEEDS INVESTIGATION** means the source is incomplete evidence or the answer depends on deployment/runtime context; **NOT TESTED** means no relevant check was performed; **NOT APPLICABLE** means the control does not apply to this Android-only repository.

| MASVS group | Status | HelloHealth evidence and limits |
|---|---|---|
| STORAGE | NEEDS INVESTIGATION | Room database is in app-private internal storage (`DatabaseModule`); backup is disabled in the manifest and extraction rules. No explicit SQLCipher/app-layer encryption was identified. OWASP MASTG allows unencrypted private-sandbox storage for L1; stronger L2 encryption depends on the chosen threat profile. Runtime storage/log inspection was not run. |
| CRYPTO | NEEDS INVESTIGATION | No custom cryptographic implementation was established as a defect by the source/scanner review. Token/key handling inside runtime SDKs, device state, and cryptographic behavior were not dynamically tested. |
| AUTH | NEEDS INVESTIGATION | Email and Google sign-in/sign-out are present. Source SQL defines owner-scoped RLS for 17 tables, but deployed policies, JWT enforcement, session lifecycle, and cross-account access were not tested. Account deletion is a confirmed gap; see HHA-001. |
| NETWORK | NEEDS INVESTIGATION | No cleartext override or custom trust-all configuration was found in the reviewed manifest/source. Runtime TLS, endpoint identity validation, redirects, and request/response data were not captured. Certificate pinning is not configured; its absence alone is not treated as a defect. |
| PLATFORM | PASSED (static), NOT TESTED (runtime) | Launcher/rationale activity is exported; workout service and startup provider are not exported. Static component review found no exposed service. Intent/deep-link fuzzing and sensitive-screen capture tests were not run. |
| CODE | FAILED (lint); NEEDS INVESTIGATION (supply chain) | `assembleDebug` succeeded; `testDebugUnitTest` passed 565 tests. `lintDebug` failed with 7 errors and 84 warnings (six API-level errors in `EmotionCaptureScreen.kt`; one health foreground-service permission error). No Gradle lockfiles or dependency verification metadata were found, limiting CVE/SCA coverage. |
| RESILIENCE | NEEDS INVESTIGATION | Release minification and resource shrinking are enabled. No signed release APK/AAB was produced because the local release keystore is absent. MobSF artifact analysis, signing verification, debuggability inspection of a release artifact, and runtime tamper/root tests were not performed. |
| PRIVACY | FAILED | No in-app account deletion request path was found, and the SQL schema does not cascade all user records from Auth deletion. This conflicts with Google Play account deletion requirements and MASVS-PRIVACY-4. AI coaching sends health-context fields to external model providers when enabled; privacy policy, Data Safety form, consent, retention, and Play Health declaration were not available for verification. |
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
