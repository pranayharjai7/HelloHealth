# Evidence index

All evidence belongs to audit revision `15171845ecf72eaea1dddfa5eb248b43652fa567`. Reports avoid secret values, tokens, signing data, personal data, and real health records.

| Evidence | Description | Redaction / Git visibility |
|---|---|---|
| `../reports/gitleaks/git-history-redacted.json` | Gitleaks full-history result; empty finding list. | No secret values; ignored by `*.json`. |
| `../reports/gitleaks/working-tree-redacted.json` | Metadata-only Gitleaks current-tree matches. | Secret/match values removed; ignored by `*.json`. |
| `../reports/trivy/filesystem-sanitized.json` | Trivy filesystem scan summary. | Sanitized; ignored by `*.json`. |
| `../reports/semgrep/semgrep-sanitized.json` | Semgrep findings/errors metadata. | Sanitized; ignored by `*.json`. |
| `../reports/build/gradle-tasks.txt` | Gradle task discovery output. | Generated build directory; ignored by `build/`. |
| `../reports/build/gradle-verification.txt` | Debug build, unit test, lint output. | Generated build directory; ignored by `build/`. |
| `../reports/build/assemble-release.txt` | Release signing-gate output. | Generated build directory; ignored by `build/`. |
| `../reports/*/report.md`, `../reports/*/README.md` | Human-readable scan/build status and limitations. | No sensitive values; visible to Git. |

The raw Gitleaks match values were never written into a Markdown report. The sanitized current-tree report retains only safe metadata such as rule category, path, and line. Local `local.properties` values remain outside Git and were not copied into this evidence directory.
