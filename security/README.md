# HelloHealth pre-Play Store security audit

## Scope

- Repository: HelloHealth Android application and the checked-in Supabase Edge Function / SQL definitions.
- Branch: `security/pre-play-store-audit`.
- Audited revision: `73ce32e7545e3eb68b1ddeee7cd0034a183e79e5` (checked-out and upstream audit branch tip).
- Audit date: 2026-11-01.
- Method: source review, local static scans, Gradle debug build/unit tests/lint, local MobSF static scan, and review against OWASP MASVS/MASTG. No production requests, credential changes, or device installs were made.
- Result: **BLOCKED**. Account deletion lifecycle is hardened in source: recursive/paginated Storage deletion (fail-closed), server-side GoTrue token revocation (fail-closed), tombstone recording, fail-closed active-account RLS policies across all 17 tables (revoked from anon), mutation triggers on all 12 text-keyed tables, and unsuppressed restrictive storage policy prevent stale-token row recreation. AI quota controls and search-path hardening are present. Debug build/tests/lint pass (575 tests, 0 lint errors). Staging verification, release signing, and Play deletion resource/declarations remain outstanding.

This is a source and local-build assessment, not a certification that the application or deployed backend is secure. Deployed Supabase state, Play Console disclosures, release signing, and runtime behavior were not verified.

## Reports

- [Security checklist](SECURITY_CHECKLIST.md)
- [Consolidated findings](SECURITY_FINDINGS.md)
- [Automated scan summary and tool matrix](reports/automated-scan-summary.md)
- [Prioritized remediation roadmap](remediation/remediation-roadmap.md)
- [Evidence index and redaction notes](evidence/README.md)
- [Tool installation notes](tools/README.md)

Individual tool/build notes are under `reports/`. Sanitized machine-readable outputs are also present there where noted in the evidence index.

## Scope limits

- Static SQL review found owner-scoped RLS enablement/policies for 17 tables in `run_sql_in_supabase.sql`. This does not establish that these definitions have been applied to the deployed project.
- The Supabase project was not accessed. No ZAP or synthetic-account test was run because no authorized staging environment was supplied; production testing was outside scope.
- No connected Android test was run because the only connected target was a physical device and the request prohibited installing on a personal phone without asking.
- No signed release artifact was produced. The release task refuses to emit an unsigned artifact while release signing configuration is unavailable. MobSF ran on the debug APK only; its report was kept outside the repository because it contains extracted strings and secret-like material.
- The repository's `.gitignore` excludes `*.json` and nested `build/` output. Sanitized JSON and Gradle evidence files exist locally but do not appear in normal Git status. The audit Markdown files are visible to Git.
- Gradle created a local `.kotlin/` directory. It was left intact; its current contents do not appear in normal Git status. No application source was modified.

## External references

- [OWASP MASVS](https://mas.owasp.org/MASVS/)
- [OWASP MASTG](https://mas.owasp.org/MASTG/)
- [OWASP mobile code review play](https://owasp.github.io/secure-agent-playbook/plugins/code-security-skills/plays/mobile-code-review.html)
- [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)
- [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en)
- [Google Play Health Apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en)
