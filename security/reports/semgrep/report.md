# Semgrep source report

- **Version:** 1.183.0, isolated Python environment outside the repository.
- **Revision / target:** `32cf0145ebcc7bc40cf5175d0daf6493b71f4f17`; 384 Git-tracked files scanned (2026-11-01 17:01 UTC).
- **Rulesets run:** `p/java`, `p/kotlin`, `p/security-audit`, `p/secrets`.
- **Result:** 0 findings, 0 scanner errors; 149 rules ran. Three files over 1 MB and 99 ignored files were skipped.
- **Metrics:** disabled.
- **Unsupported ruleset:** `p/android` returned HTTP 404 from the registry and was not counted as coverage.

The Kotlin ruleset is small and a clean result is not proof of safety. Dynamic Android behavior, the deployed Supabase function, and third-party dependency CVEs require separate checks. Sanitized output: `semgrep-sanitized.json` (ignored by the repository's `*.json` rule).
