# Evidence index

The current checked-in audit reports cover source revision `9300eaf2bbe4c43836982be147f5135ff17cb705`. Reports avoid secret values, tokens, signing data, personal data, and real health records. Ignored machine-readable JSON and build artifacts are local-only and may predate this rerun; the latest executed results are summarized in `../reports/automated-scan-summary.md`.

| Evidence | Description | Redaction / Git visibility |
|---|---|---|
| `../reports/gitleaks/git-history-redacted.json` | Prior local Gitleaks full-history result; latest rerun summary is in `../reports/gitleaks/report.md`. | No secret values; ignored by `*.json`. |
| `../reports/gitleaks/working-tree-redacted.json` | Prior local metadata-only Gitleaks current-tree matches; latest rerun summary is in `../reports/gitleaks/report.md`. | Secret/match values removed; ignored by `*.json`. |
| `../reports/trivy/filesystem-sanitized.json` | Prior local Trivy filesystem result; latest rerun summary is in `../reports/trivy/report.md`. | Sanitized; ignored by `*.json`. |
| `../reports/semgrep/semgrep-sanitized.json` | Prior local Semgrep result; latest rerun summary is in `../reports/semgrep/report.md`. | Sanitized; ignored by `*.json`. |
| `../reports/build/gradle-tasks.txt` | Gradle task discovery output. | Generated build directory; ignored by `build/`. |
| `../reports/build/gradle-verification.txt` | Debug build, unit test, lint output. | Generated build directory; ignored by `build/`. |
| `../reports/build/assemble-release.txt` | Release signing-gate output. | Generated build directory; ignored by `build/`. |
| `../reports/*/report.md`, `../reports/*/README.md` | Human-readable scan/build status and limitations. | No sensitive values; visible to Git. |

The raw Gitleaks match values were never written into a Markdown report. Local `local.properties` values remain outside Git and were not copied into this evidence directory.
