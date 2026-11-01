# MobSF status — audit rerun

- **Tool:** MobSF via local Docker image `opensecurity/mobile-security-framework-mobsf:latest`.
- **Execution:** Completed locally; service bound to `127.0.0.1:8000` and stopped after analysis.
- **Artifact:** `app/build/outputs/apk/debug/app-debug.apk`; package `com.hellohealth`, version `1.2.0` (code 3).
- **Result:** MobSF score 49; 3 high, 11 warning, 1 informational, 1 hotspot, and 1 secure item in its app security summary. The high results include debug certificate, `android:debuggable=true`, and debug configuration, as expected for a debug APK. Generic warnings require manual triage and are not asserted as release defects.
- **Limit:** This was not a signed release APK/AAB. No conclusion about release signing, debuggability, or final package posture can be made. `:app:assembleRelease` remains blocked because release signing configuration is unavailable.
- **Evidence handling:** The raw MobSF response includes extracted strings and secret-like material and is retained outside the repository only. No raw JSON, extracted strings, secret values, or credentials are committed. The scanner result is static analysis only; no dynamic device analysis was performed.

Run MobSF again on a signed release candidate and retain only a reviewed, sanitized summary.
