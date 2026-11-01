# Semgrep source report

- **Version:** 1.183.0, isolated Python environment outside the repository.
- **Rulesets run:** `p/java`, `p/kotlin`, `p/security-audit`, `p/secrets`.
- **Result:** 0 findings, 0 scanner errors for the completed rulesets.
- **Metrics:** disabled.
- **Unsupported ruleset:** `p/android` returned HTTP 404 from the registry and was not counted as coverage.

The Kotlin ruleset is small and a clean result is not proof of safety. Dynamic Android behavior, the deployed Supabase function, and third-party dependency CVEs require separate checks. Sanitized output: `semgrep-sanitized.json` (ignored by the repository's `*.json` rule).
