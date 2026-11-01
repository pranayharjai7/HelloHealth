# Gitleaks report

- **Version:** 8.30.1 (installed outside the app repository)
- **Revision:** `15171845ecf72eaea1dddfa5eb248b43652fa567`
- **History scan:** full Git history (`--all --full-history`), redacted; 0 findings.
- **Filesystem scan:** 36 heuristic matches; values are not retained in this report.

The current-tree matches were limited to ignored local materials: 10 in IDE planning settings, 22 in generated build outputs, and 4 in ignored `local.properties`. The latter contains local API configuration values; the scanner did not establish that any match is a credential committed to Git or embedded in the release app. No secret value is printed. No confirmed history exposure was found, so this scan alone does not justify rotating a credential.

Sanitized reports: `git-history-redacted.json` and `working-tree-redacted.json`. These files are ignored by the repository's `*.json` rule.
