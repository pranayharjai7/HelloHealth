# Automated scan and build summary

- **Date:** 2026-11-01 (UTC report timestamp: 2026-11-01 17:04 UTC; see local artifact timestamps for scan outputs)
- **Branch / revision:** `security/pre-play-store-audit` / `32cf0145ebcc7bc40cf5175d0daf6493b71f4f17`
- **Target:** HelloHealth Android source tree, checked-in SQL and Supabase Edge Function, Gradle debug build/test/lint tasks.
- **No production Supabase or LLM provider endpoint was contacted.** No secrets or health records are reproduced here.

## Tool execution matrix

| Tool | Installed | Executed | Run time (UTC) | Target | Result | Limitations |
|---|---|---|---|---|---|---|
| MobSF | Yes (local Docker) | Prior scan only | Earlier 2026-11-01 rerun | `app/build/outputs/apk/debug/app-debug.apk` | Prior debug-only result reused as context | The latest commits change Supabase source/docs and do not change the APK. The debug APK evidence is therefore contextual, not a fresh analysis of the backend changes. Prior MobSF result: HelloHealth 1.2.0 (code 3), score 49; debug signing/debuggable findings expected for debug. Raw report excluded. No release artifact is available. |
| Gitleaks 8.30.1 | Yes, outside repo | Yes | 2026-11-01 17:01 UTC (history); 17:04 UTC (filesystem) | Current repository filesystem and full Git history (242 commits) | History: 1 `generic-api-key` heuristic on descriptive report text in the audited commit, manually confirmed not a credential. Final filesystem scan: 35 heuristics in ignored IDE settings, generated build outputs, or ignored local config. | Values were redacted. Raw output is outside the repository. Gitleaks returned exit 1 because heuristic matches were reported. |
| Trivy 0.76.1 | Yes, outside repo | Yes | 2026-11-01 17:01 UTC | Filesystem scan; excluded `.git`, build outputs, `androidx`, `.artifacts`, and `*.hprof`; scanners: vulnerability, misconfiguration, secret | 0 vulnerabilities, 0 misconfigurations, 0 secret matches | Scanner detected 0 language-specific and 0 config files; no Gradle lockfiles/dependency verification metadata or SBOM, so dependency CVE coverage is incomplete. |
| Semgrep 1.183.0 | Yes, isolated Python venv outside repo | Yes | 2026-11-01 17:01 UTC | 384 Git-tracked files; Java/Kotlin/security source rules | 0 findings, 0 scan errors using `p/java`, `p/kotlin`, `p/security-audit`, `p/secrets` | Three files over 1 MB and 99 ignored files were skipped. `p/android` returned HTTP 404. Kotlin pack has limited rule breadth; static analysis does not establish runtime safety. Metrics were disabled. |
| OWASP ZAP | No | No | 2026-11-01; exact attempt time not retained | Intended: authorized staging API | Not tested | No authorized staging endpoint supplied; production was not tested. Winget installer required administrator elevation. A direct installer launch was blocked by tool policy. |
| OWASP MASVS/MASTG + secure-agent mobile review play | Reference repositories cloned outside app; web guides consulted | Yes, source-only review | 2026-11-01; exact review time not retained | Android source/manifest, SQL and Edge Function | Findings and 8 control groups mapped in `SECURITY_CHECKLIST.md` | No full device/runtime MASTG execution. The OWASP guide states source-only mobile review does not cover remote endpoints. |
| Gradle task discovery | Gradle 9.0.0 | Yes | prior audit | `gradlew.bat tasks --all` | Passed | Task discovery only. |
| Gradle debug build, tests, lint (latest branch) | Gradle 9.0.0; AGP 8.9.3; Kotlin 2.0.21 | Yes | 2026-11-01 17:02 UTC | `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` | Debug APK, unit tests, and lint all PASSED | 575 tests / 99 suites passed (0 failures); lint: 0 errors, 82 warnings (all 7 original lint errors resolved). |
| Gradle release build | Gradle 9.0.0 | Yes | 2026-11-01 rerun | `:app:assembleRelease` | Failed at the signing gate | No release keystore configured; task refused to emit an unsigned artifact. No key was created or modified. |

## Commands and evidence

Commands run from the repository root unless stated otherwise:

- `gitleaks git --log-opts "--all --full-history" --redact=100` — 242 commits scanned; one false-positive `generic-api-key` heuristic on descriptive report prose, no credential.
- Gitleaks filesystem scan of the final current repository tree with redaction — 35 heuristics, all in ignored IDE configuration, generated build outputs, or ignored local configuration. No confirmed credential; reports remain outside the repository.
- `trivy fs --scanners vuln,misconfig,secret ...` — repository filesystem with generated/vendor directories excluded; 0 reported findings. Trivy detected no language-specific or config files, and dependency CVE coverage remains incomplete.
- `semgrep scan --config p/java --config p/kotlin --config p/security-audit --config p/secrets --metrics off ...` — 384 tracked files, 0 findings, 0 scan errors; three files over 1 MB and 99 ignored files were skipped. A separate `p/android` lookup returned HTTP 404.
- `.\gradlew.bat tasks --all` — task discovery (prior audit UTC).
- `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain` — debug build, unit tests, and lint (2026-11-01 rerun UTC); passed; 575 tests / 99 suites, lint 0 errors and 82 warnings.
- `.\gradlew.bat :app:assembleRelease --console=plain` — refused due to missing release keystore (2026-11-01 rerun UTC).

All times above are UTC on 2026-11-01. Prior-run timestamps retained in this table are from the earlier audit. Tool versions confirmed for the audit: Gitleaks 8.30.1, Trivy 0.76.1, Semgrep 1.183.0, Gradle 9.0.0, Android Gradle Plugin 8.9.3, Kotlin 2.0.21. Project targets SDK 36, minSdk 26, and uses Java 17 source/bytecode compatibility. Gradle launcher ran on Java 25; the configured daemon JVM is Java 21-compatible.

Generated JSON and Gradle build evidence files are locally present but ignored by the repository's `*.json` and `build/` ignore rules. See [evidence index](../evidence/README.md) and per-tool notes for precise limitations.


MobSF raw output is not retained in Git. The report markdown contains only a sanitized summary.
## Latest commit review

- Current revision: `32cf0145ebcc7bc40cf5175d0daf6493b71f4f17` on the audit branch.
- Re-ran debug assembly, unit tests, and lint: passed; 575 tests / 99 suites, no failures; lint 0 errors and 82 warnings.
- Re-ran Gitleaks: one history heuristic manually confirmed as descriptive report text, and 35 filesystem heuristics in ignored/generated/local files, with no confirmed credential. Trivy: 0 findings for selected scanners. Semgrep: 0 findings and 0 scan errors for selected rules.
- `is_active_account` now fails closed, anonymous execute access is revoked, and Storage RLS DDL errors are not swallowed. HHA-001 remains open because `delete-account` calls global sign-out twice with the same JWT and may return 500 before Auth identity deletion.
- No Deno/Supabase CLI or staging project was available; Edge Function typechecking, migration execution, and stale-token/Storage integration tests were not performed. Staging verification remains pending.
