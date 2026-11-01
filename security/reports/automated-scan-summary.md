# Automated scan and build summary

- **Date:** 2026-11-01 (UTC report timestamp: 2026-11-01 15:24 UTC; see local artifact timestamps for scan outputs)
- **Branch / revision:** `security/pre-play-store-audit` / `73ce32e7545e3eb68b1ddeee7cd0034a183e79e5`
- **Target:** HelloHealth Android source tree, checked-in SQL and Supabase Edge Function, Gradle debug build/test/lint tasks.
- **No production Supabase or LLM provider endpoint was contacted.** No secrets or health records are reproduced here.

## Tool execution matrix

| Tool | Installed | Executed | Run time (UTC) | Target | Result | Limitations |
|---|---|---|---|---|---|---|
| MobSF | Yes (local Docker) | Prior scan only | Earlier 2026-11-01 rerun | `app/build/outputs/apk/debug/app-debug.apk` | Prior debug-only result reused as context | The latest commits change Supabase source/docs and do not change the APK. The debug APK evidence is therefore contextual, not a fresh analysis of the backend changes. Prior MobSF result: HelloHealth 1.2.0 (code 3), score 49; debug signing/debuggable findings expected for debug. Raw report excluded. No release artifact is available. |
| Gitleaks 8.30.1 | Yes, outside repo | Yes | Latest rerun 15:23 UTC | Current repository filesystem and full Git history | History: 0 findings. Filesystem: 35 heuristic matches, all in ignored IDE settings, generated build outputs, or ignored local config; no confirmed committed secret. | Values were redacted. Local config and generated artifacts produced false-positive/irrelevant matches. Raw output is not stored. |
| Trivy 0.76.1 | Yes, outside repo | Yes | 2026-11-01 15:22 UTC | Filesystem scan; excluded `.git`, build outputs, `androidx`, `.artifacts`, and `*.hprof`; scanners: vulnerability, misconfiguration, secret | 0 vulnerabilities, 0 misconfigurations, 0 secret matches | No Gradle lockfiles/dependency verification metadata or SBOM; dependency CVE coverage is incomplete. |
| Semgrep 1.183.0 | Yes, isolated Python venv outside repo | Yes | 2026-11-01 15:24 UTC | Java/Kotlin/security source rules | 0 findings using `p/java`, `p/kotlin`, `p/security-audit`, `p/secrets` | `p/android` returned HTTP 404. Kotlin pack has limited rule breadth; static analysis does not establish runtime safety. Metrics were disabled. |
| OWASP ZAP | No | No | 2026-11-01; exact attempt time not retained | Intended: authorized staging API | Not tested | No authorized staging endpoint supplied; production was not tested. Winget installer required administrator elevation. A direct installer launch was blocked by tool policy. |
| OWASP MASVS/MASTG + secure-agent mobile review play | Reference repositories cloned outside app; web guides consulted | Yes, source-only review | 2026-11-01; exact review time not retained | Android source/manifest, SQL and Edge Function | Findings and 8 control groups mapped in `SECURITY_CHECKLIST.md` | No full device/runtime MASTG execution. The OWASP guide states source-only mobile review does not cover remote endpoints. |
| Gradle task discovery | Gradle 9.0.0 | Yes | prior audit | `gradlew.bat tasks --all` | Passed | Task discovery only. |
| Gradle debug build, tests, lint (latest branch) | Gradle 9.0.0; AGP 8.9.3; Kotlin 2.0.21 | Yes | 2026-11-01 15:22 UTC | `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` | Debug APK, unit tests, and lint all PASSED | 575 tests / 99 suites passed (0 failures); lint: 0 errors, 82 warnings (all 7 original lint errors resolved). |
| Gradle release build | Gradle 9.0.0 | Yes | 2026-11-01 rerun | `:app:assembleRelease` | Failed at the signing gate | No release keystore configured; task refused to emit an unsigned artifact. No key was created or modified. |

## Commands and evidence

Commands run from the repository root unless stated otherwise:

- `gitleaks git --log-opts "--all --full-history" --redact=100` — full commit history; sanitized empty report at `reports/gitleaks/git-history-redacted.json` (12:16:37 UTC).
- Gitleaks filesystem scan of the current repository with redaction — sanitized metadata-only report at `reports/gitleaks/working-tree-redacted.json` ; rerun produced 35 heuristic matches, none confirmed as committed credentials.
- `trivy fs --scanners vuln,misconfig,secret ...` — repository filesystem with generated/vendor directories excluded; sanitized report at `reports/trivy/filesystem-sanitized.json` (2026-11-01 rerun UTC).
- `semgrep scan --config p/java --config p/kotlin --config p/security-audit --config p/secrets --metrics off ...` — source scan; sanitized report at `reports/semgrep/semgrep-sanitized.json` (2026-11-01 rerun UTC). A separate `p/android` lookup returned HTTP 404.
- `.\gradlew.bat tasks --all` — task discovery (prior audit UTC).
- `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain` — debug build, unit tests, and lint (2026-11-01 rerun UTC); passed; 575 tests / 99 suites, lint 0 errors and 82 warnings.
- `.\gradlew.bat :app:assembleRelease --console=plain` — refused due to missing release keystore (2026-11-01 rerun UTC).

All times above are UTC on 2026-11-01. Prior-run timestamps retained in this table are from the earlier audit; rerun times are date-only where exact timestamps were not retained. Tool versions confirmed for the audit: Gitleaks 8.30.1, Trivy 0.76.1, Semgrep 1.183.0, Gradle 9.0.0, Android Gradle Plugin 8.9.3, Kotlin 2.0.21. Project targets SDK 36, minSdk 26, and uses Java 17 source/bytecode compatibility. Gradle launcher ran on Java 25; the configured daemon JVM is Java 21-compatible.

Generated JSON and Gradle build evidence files are locally present but ignored by the repository's `*.json` and `build/` ignore rules. See [evidence index](../evidence/README.md) and per-tool notes for precise limitations.


MobSF raw output is not retained in Git. The report markdown contains only a sanitized summary.
## Latest commit review

- Current revision: `73ce32e7545e3eb68b1ddeee7cd0034a183e79e5` on the audit branch.
- Re-ran debug assembly, unit tests, and lint: passed; 575 tests / 99 suites, no failures; lint 0 errors and 82 warnings.
- Re-ran Gitleaks: 0 full-history findings and 35 current-filesystem heuristic matches. Trivy: 0 findings for selected scanners. Semgrep: 0 findings and 0 scanner errors for selected rules.
- Source remediation complete for HHA-001 and HHA-005: `public.is_active_account` strictly fails closed (`COALESCE(v_exists, false)`), anonymous execute permissions were revoked (closing HHA-005), Storage RLS policy installation is unsuppressed and fails closed on DDL error, and Edge Function `delete-account` explicitly revokes server refresh tokens via GoTrue `POST /auth/v1/logout?scope=global` and admin SDK.
- No Deno/Supabase CLI or staging project was available; Edge Function typechecking, migration execution, and stale-token/Storage integration tests were not performed. Staging verification remains pending.
