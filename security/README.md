# HelloHealth pre-Play Store security audit

## Scope

- Repository: HelloHealth Android application and the checked-in Supabase Edge Function / SQL definitions.
- Branch: `security/pre-play-store-audit`.
- Audited revision: `15171845ecf72eaea1dddfa5eb248b43652fa567` (the current `master` and `origin/master` tip at audit time).
- Audit date: 2026-11-01.
- Method: source review, local static scans, Gradle debug build/unit tests/lint, and review against OWASP MASVS/MASTG. No app/backend changes, production requests, credential changes, device installs, commits, or pushes were made.
- Result: **BLOCKED**. One confirmed High Play release blocker remains, lint has errors, and signed release artifact / APK analysis / runtime backend checks were unavailable.

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
- No signed release artifact was produced. The release task intentionally refuses to emit an unsigned artifact when the configured release keystore is absent.
- The repository's `.gitignore` excludes `*.json` and nested `build/` output. Sanitized JSON and Gradle evidence files exist locally but do not appear in normal Git status. The audit Markdown files are visible to Git.
- Gradle created a local `.kotlin/` directory. It was left intact; its current contents do not appear in normal Git status. No application source was modified.

## External references

- [OWASP MASVS](https://mas.owasp.org/MASVS/)
- [OWASP MASTG](https://mas.owasp.org/MASTG/)
- [OWASP mobile code review play](https://owasp.github.io/secure-agent-playbook/plugins/code-security-skills/plays/mobile-code-review.html)
- [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)
- [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en)
- [Google Play Health Apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en)
