# Consolidated security findings

Audit revision: `69ddd1553ed2d109c72cd9da98fe6e6f3b4d54b1` on `security/pre-play-store-audit`.

## Severity summary

| Critical | High | Medium | Low | Informational |
|---:|---:|---:|---:|---:|
| 0 | 1 | 2 | 1 | 0 |

The audit is **BLOCKED for Play release**. The open findings below require correction or verification. Static scan/build results and scope limits are summarized in [the audit summary](reports/automated-scan-summary.md).

## HHA-001 — Account deletion does not reliably remove all user data or the Auth identity

- **Severity:** High — release blocker
- **Category:** Privacy, account lifecycle, data retention
- **Status:** Partially implemented in source; release guarantees are not met.
- **Affected components:** `supabase/functions/delete-account/index.ts`, `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql`, `run_sql_in_supabase.sql`, Android account deletion flow.
- **Evidence:** The in-app flow and server function now exist. However, SQL directly deletes matching rows from `storage.objects`. Supabase documents that SQL metadata deletion does not remove stored bytes and can orphan objects; Storage API deletion is required. No storage upload usage was found in Android source, but deployed buckets and other writers were not inspected. The Edge Function conditionally deletes the Auth user only when `SUPABASE_SERVICE_ROLE_KEY` is present, yet returns success without deleting the identity when it is absent. Supabase also states existing access JWTs remain valid until expiry after user deletion; access through those tokens and affected RLS policies was not tested. The migration/function are source files only: deployment and end-to-end staging verification are unconfirmed.
- **Impact:** A successful response can overstate deletion: object bytes or the Auth identity may remain, and existing tokens may continue authorizing requests until expiry. Health and account data could remain accessible or retained after a user requests deletion.
- **Required:** Delete Storage objects through the Supabase Storage API; make missing admin configuration a hard failure before reporting success; define/implement stale-token handling and verify RLS behavior through expiry; deploy and exercise deletion with synthetic staging accounts; publish the Google Play account deletion web resource and complete its declarations.
- **References:** [Supabase delete objects](https://supabase.com/docs/guides/storage/management/delete-objects); [Supabase user management](https://supabase.com/docs/guides/auth/managing-user-data); [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en).

## HHA-002 — AI coaching quota enforcement fails open and is raceable

- **Severity:** Medium
- **Category:** Backend API resource consumption / provider-cost abuse
- **Status:** Partially implemented in source; server-side enforcement is not reliable.
- **Affected components:** `supabase/functions/ai-coach/index.ts`, `supabase/migrations/20261101140000_account_deletion_and_ai_guardrails.sql`.
- **Evidence:** Input count/length and generation parameter caps were added. But an RPC error or missing quota result does not reject the request; provider calls continue unmetered. The SQL quota routine performs count, comparison, then insert without a lock or atomic reservation, allowing concurrent calls to pass the same limit. The function also reads the entire request with `req.text()` before checking its size, so the 32 KB check does not bound buffering for missing/false Content-Length. If Supabase URL/anon-key configuration is absent, authentication and quota checks are skipped and execution can continue. Deployment gateway settings were not inspected.
- **Impact:** Requests can bypass quotas during database/configuration failure, exceed limits concurrently, or cause excessive request-body buffering; provider cost and resource use remain exposed.
- **Required:** Fail closed on quota/auth configuration errors; implement a concurrency-safe atomic quota reservation; enforce a streaming byte limit before buffering; reject missing required Supabase configuration. Test failure, concurrent, and chunked-body cases in authorized staging.
- **References:** [Supabase Edge Function authentication](https://supabase.com/docs/guides/functions/auth); [OWASP API4:2023](https://owasp.org/API-Security/editions/2023/en/0xa4-unrestricted-resource-consumption/).

## HHA-003 — SECURITY DEFINER functions use exposed schema and non-empty search paths

- **Severity:** Medium
- **Category:** Database function hardening
- **Status:** Open hardening issue.
- **Affected components:** `public.delete_user_account(uuid)`, `public.check_and_record_ai_usage(text, integer, integer)`.
- **Evidence:** Both functions are `SECURITY DEFINER` in exposed `public` and set `search_path` to `public` plus other schemas. The code schema-qualifies its table references and applies user checks, which reduces demonstrated exploitability; deployed schema/privileges were not inspected. Supabase guidance recommends avoiding SECURITY DEFINER in exposed schemas and setting an empty search path with schema-qualified references.
- **Required:** Move privileged routines to a non-exposed schema where feasible, use `SET search_path = ''`, schema-qualify every reference, and review execute grants against deployed roles.
- **Reference:** [Supabase database functions](https://supabase.com/docs/guides/database/functions).

## HHA-004 — MobSF analysis used a debug APK, not a release candidate

- **Severity:** Low (evidence gap; not a demonstrated release vulnerability)
- **Category:** Release artifact assurance
- **Status:** Open verification item.
- **Evidence:** Local MobSF static analysis ran on `app-debug.apk` (package `com.hellohealth`, version `1.2.0`, code 3). MobSF reported expected debug signing and debuggable configuration warnings, plus generic static warnings. These results cannot establish release artifact posture. `:app:assembleRelease` remains blocked by missing release signing configuration.
- **Required:** Produce a properly signed release candidate through the established secret-safe process, verify signing/debug flags, then rerun MobSF and retain a sanitized summary.

## Observations not promoted to findings

- Gitleaks full history scan found no matches. Current-tree heuristics (35) were confined to generated build output, IDE settings, and ignored local configuration; no confirmed committed credential was identified. No rotation is recommended from this scan alone.
- Trivy reported no filesystem vulnerabilities, misconfigurations, or secrets in scanned inputs. Dependency CVE coverage remains incomplete without lockfiles, verification metadata, or a release SBOM.
- Semgrep reported zero findings for the selected Java, Kotlin, security-audit, and secrets rules. The Android pack lookup returned HTTP 404, and this is not proof of safety.
- MobSF reported three high findings on the debug APK (debug signing, debuggable flag, debug configuration), 11 warnings, one informational finding, and a score of 49. Debug-only findings are not attributed to a release artifact. The raw report was not committed because it contains extracted strings and secret-like material.
- Room database encryption, Health Connect runtime permission behavior, deployed RLS/JWT configuration, privacy disclosures, and Play Console declarations need further investigation; see the checklist.
