# Gitleaks report

- **Version:** 8.30.1 (installed outside the app repository)
- **Revision:** `32cf0145ebcc7bc40cf5175d0daf6493b71f4f17`
- **History scan:** full Git history (`--all --full-history`), 242 commits scanned; one `generic-api-key` heuristic in descriptive security-report prose, manually reviewed as non-secret text.
- **Filesystem scan:** 35 heuristic matches; values are not retained in this report.

All current-tree matches were in ignored IDE settings, generated build outputs, or ignored local configuration. The scanner did not establish that any match is a credential committed to Git or embedded in the release app. No secret value is printed. No confirmed history exposure was found, so this scan alone does not justify rotating a credential.

Sanitized reports: `git-history-redacted.json` and `working-tree-redacted.json`. These files are ignored by the repository's `*.json` rule.
