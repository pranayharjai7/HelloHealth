# Audit tools and references

## Installed outside the application repository

The external working directory is `C:\Pranay\Projects\AntiGravityProjects\hellohealth-security-tools`; tool binaries, virtual environments, and OWASP clones are not part of the HelloHealth source tree.

| Tool/reference | State | Notes |
|---|---|---|
| Gitleaks 8.30.1 | Installed and run | Git history and current filesystem; values redacted. |
| Trivy 0.76.1 | Installed and run | Filesystem vuln/misconfiguration/secret scanners; dependency graph coverage limited by missing lockfiles/SBOM. |
| Semgrep 1.183.0 | Installed in isolated Python venv and run | Java/Kotlin/security packs; metrics disabled; `p/android` registry request returned 404. |
| Docker Desktop 29.4.3 client | Installed; Engine unavailable | Started hidden for MobSF attempt. Local Docker Engine API returned HTTP 500. |
| MobSF | Not installed/run | Official source cloned outside app; Docker unavailable and native Windows setup incomplete. |
| OWASP ZAP 2.17 package | Not installed/run | Installer required administrator elevation. Direct installer launch was blocked by tool policy. |
| OWASP MASVS | Cloned outside app; reviewed | Reference controls for the eight groups are mapped in `../SECURITY_CHECKLIST.md`. |
| OWASP MASTG | Cloned outside app; reviewed | Used for source/runtime test planning; runtime tests not performed. |
| OWASP Secure Agent Playbook | Online guide reviewed | Mobile Code Review play consulted as a systematic source-only review guide. |

OWASP reference clone revisions: MASVS `bbe226fd17e6dab51927f392936deef31c816423`; MASTG `f6a38260006eeaecdad94350ab370b1631f365ae`; MobSF source `819c5e87dfebb1d43de14a9b1fa9a5ad69d44fe3`.

The Docker client can exist while its Engine is unavailable. No attempt was made to bypass elevation, expose MobSF beyond loopback, or scan production. No project dependencies were upgraded and no secrets were written to this directory.

## Commands recorded

See `../reports/automated-scan-summary.md` for commands, versions, targets, and results. The actual safe report artifacts are under `../reports/`; JSON and nested `build/` files are ignored by the repository's `.gitignore`.
