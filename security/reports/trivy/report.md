# Trivy filesystem report

- **Version:** 0.76.1 (installed outside the app repository)
- **Target:** HelloHealth filesystem at `15171845ecf72eaea1dddfa5eb248b43652fa567`.
- **Scanners:** vulnerability, misconfiguration, secret.
- **Exclusions:** `.git`, build outputs, `androidx`, `.artifacts`, and `*.hprof`.
- **Result:** 0 vulnerabilities, 0 misconfigurations, 0 secret matches.

This does not establish a complete dependency CVE inventory. The repository has no Gradle dependency lockfiles or verification metadata, and no release SBOM was available. No dependency was changed. Sanitized output: `filesystem-sanitized.json` (ignored by the repository's `*.json` rule).
