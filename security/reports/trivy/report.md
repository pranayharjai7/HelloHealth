# Trivy filesystem report

- **Version:** 0.76.1 (installed outside the app repository)
- **Target:** HelloHealth filesystem at `9300eaf2bbe4c43836982be147f5135ff17cb705` (rerun 2026-11-01 17:34 UTC).
- **Scanners:** vulnerability, misconfiguration, secret.
- **Exclusions:** `.git`, build outputs, `androidx`, `.artifacts`, and `*.hprof`.
- **Result:** 0 vulnerabilities, 0 misconfigurations, 0 secret matches.
- **Coverage note:** The scanner detected 0 language-specific files and 0 config files in the selected inputs; this did not provide resolved Gradle dependency CVE coverage.

This does not establish a complete dependency CVE inventory. The repository has no Gradle dependency lockfiles or verification metadata, and no release SBOM was available. No dependency was changed. Sanitized output: `filesystem-sanitized.json` (ignored by the repository's `*.json` rule).
