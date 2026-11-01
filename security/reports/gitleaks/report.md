# Gitleaks report

- **Version:** 8.30.1 (installed outside the app repository)
- **Revision:** `9300eaf2bbe4c43836982be147f5135ff17cb705`
- **History scan:** full Git history (`--all --full-history`), 244 commits scanned at 2026-11-01 17:34 UTC; one `generic-api-key` heuristic in descriptive security-report prose from an earlier commit, manually reviewed as non-secret text.
- **Filesystem scan:** 35 heuristic matches at 2026-11-01 17:40 UTC; values are not retained in this report.

All current-tree matches were in ignored IDE settings, generated build outputs, or ignored local configuration. The scanner did not establish that any match is a credential committed to Git or embedded in the release app. No secret value is printed. No confirmed history exposure was found, so this scan alone does not justify rotating a credential.

Sanitized reports: `git-history-redacted.json` and `working-tree-redacted.json`. These files are ignored by the repository's `*.json` rule.
