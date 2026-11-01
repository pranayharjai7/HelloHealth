# Consolidated security findings

Audit revision: `15171845ecf72eaea1dddfa5eb248b43652fa567` on `security/pre-play-store-audit`.

## Severity summary

| Critical | High | Medium | Low | Informational |
|---:|---:|---:|---:|---:|
| 0 | 1 | 1 | 0 | 0 |

Build/lint failures and scanner limitations are recorded in the checklist and build/tool reports. They are not counted as security vulnerabilities.

## HHA-001 — No account deletion request flow; user data is not consistently linked for deletion

- **Severity:** High — release blocker
- **Category:** Privacy, account lifecycle, data retention
- **Affected components:** `app/src/main/java/com/hellohealth/domain/repository/AuthRepository.kt`, `app/src/main/java/com/hellohealth/data/repository/AuthRepositoryImpl.kt`, account UI, `run_sql_in_supabase.sql`
- **Status:** Confirmed in repository source; deployed state and any external deletion website were not verified.
- **Evidence / reproduction:** The auth contract and implementation provide sign-up, sign-in, Google sign-in, sign-out, and profile update operations, but no account-deletion operation or in-app deletion request was found. In `run_sql_in_supabase.sql`, only a subset of user tables has an Auth foreign key with `ON DELETE CASCADE`; multiple health-related tables use `user_id text` without an Auth foreign key, and `nutrition_goals` has an Auth foreign key without cascade. Search the auth interface/implementation and inspect the schema's foreign keys to reproduce.
- **Impact:** Users cannot initiate account deletion from the app. If an account is deleted separately, health and profile-related rows may remain or prevent deletion. Google Play requires apps that allow account creation to provide an in-app deletion request path and a web resource for deletion. Health information can remain associated with an account after the user expects removal.
- **Recommended remediation:** Add a clearly reachable in-app deletion request. Implement an authenticated server-side deletion workflow that removes the Auth identity and every associated row/object, including tables without Auth foreign keys, and document any legally required retention. Provide the required web deletion resource and update Play disclosures.
- **Fix verification:** In an authorized staging project, create a synthetic account, populate every user-owned table/object, request deletion from the app and web resource, then verify the identity and all associated data are deleted (or documented retention is applied). Confirm repeated requests are idempotent and update Play Console declarations.
- **OWASP mapping:** MASVS-PRIVACY-4; MASWE-0076; CWE-359 per the MASWE mapping. The current MASWE-0076 entry has no linked MASTG atomic test, so verify this control manually; no MASTG test ID is claimed.
- **Tool/source:** Manual review of the auth repository/UI and SQL schema; [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en); [MASVS-PRIVACY-4](https://mas.owasp.org/MASVS/controls/MASVS-PRIVACY-4/); [MASWE-0076](https://mas.owasp.org/MASWE/MASVS-PRIVACY/MASWE-0076/).

## HHA-002 — AI coaching requests have no source-level size, count, or per-user rate limits

- **Severity:** Medium
- **Category:** Backend API resource consumption / provider-cost abuse
- **Affected component:** `supabase/functions/ai-coach/index.ts`
- **Status:** Confirmed source-level gap; effective deployed gateway limits, JWT enforcement, and provider quotas were not verified.
- **Evidence / reproduction:** The handler checks only that `messages` is a non-empty array. It passes user-supplied message text, message count, `temperature`, and `maxOutputTokens` to Gemini/OpenRouter; the token budget is not clamped in the function. No per-user rate/usage limit is visible in this function. The source comment says JWT verification is enabled at deployment, but deployment configuration was not inspected.
- **Impact:** An authenticated user who can call the function directly may submit oversized/repeated requests or a very high output-token budget, increasing provider cost and consuming shared quota. This is a risk in the implementation; no live exploit or billing impact was tested.
- **Recommended remediation:** Enforce body/message count and text-size limits server-side, allowlist/clamp model parameters and output-token budgets, apply per-user and global quotas/rate limits, and alert on abnormal provider usage. Confirm JWT verification and gateway limits in the deployed function configuration.
- **Fix verification:** Exercise boundary and abuse cases against an authorized staging function using a synthetic account; verify oversized inputs are rejected, output budgets are capped, authenticated requests are rate-limited, and no request reaches a provider after rejection.
- **OWASP mapping:** Backend function, outside MASVS/MASTG mobile-app test scope; OWASP API Security Top 10 API4:2023 (Unrestricted Resource Consumption); CWE-400.
- **Tool/source:** Manual review of `supabase/functions/ai-coach/index.ts`; no ZAP or live test was performed.

## Observations not promoted to security findings

- **Room database encryption:** `DatabaseModule` builds `hello_health.db` with ordinary Room in app-private storage. No explicit database encryption was found. OWASP MASTG notes unencrypted private-sandbox storage can meet L1; L2 requires stronger encryption. Choose a target profile and threat model, then dynamically inspect stored health data. This is not counted as a defect on current evidence. References: MASVS-STORAGE-1 and MASTG-TEST-0304/0306.
- **Third-party health-data processing:** When AI coaching is enabled, the app sends selected daily health context through its Edge Function to Gemini/OpenRouter. Verify privacy policy, user disclosures/consent, Data Safety answers, vendor retention, and deletion coverage before release. Runtime data-flow and Play Console declarations were not available.
- **Health permissions:** The manifest declares broad Health Connect read permissions. Confirm each permission is tied to a user-facing feature and complete the Play Health Apps declaration.
- **Client-side Maps key:** A Maps key is present as an Android client configuration value. It is not automatically a server secret; verify package/signing-certificate and API restrictions in the owning cloud project.
- **Gitleaks current-tree matches:** Scanner heuristics were limited to ignored local IDE/config/build files. Values were redacted. No current match was confirmed as a Git-history leak; no rotation is recommended from this scan alone.
