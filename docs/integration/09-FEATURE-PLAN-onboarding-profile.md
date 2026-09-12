# 09 — Feature Plan: New‑account Onboarding + Profile model  · Phase P0.5

Capture the vital user information the app needs to function correctly (gender, age,
height, current weight, activity level, goals) the first time a new account signs in,
and extend the profile model those numbers feed. **Depends on P0** (offline‑first spine).

**Why this is its own early phase:** HelloHealth's `UserProfile` currently holds *only*
`displayName` — there is no gender/age/weight/height/goal anywhere. Nutrition's BMR/TDEE
budget, the readiness score, and the body‑analytics engine all need this; the ported
TrackMe code hardcodes **age‑25/male** today precisely because the data doesn't exist.
Capturing it once, up front, unblocks P5/P6 and improves P3.

## READ
- HelloHealth: `domain/model/UserProfile.kt` (only `displayName`), `domain/model/User.kt`, `domain/model/ActivityGoals.kt` (defaults 10000/500/60), `domain/repository/ProfileRepository.kt` (`getProfile`/`upsertProfile`), `ProfileRepositoryImpl` + the `profiles` Supabase DTO/table, `ui/profile/ProfileScreen.kt` + `ProfileEditorState.kt`, `ui/goals/*` (goal editing patterns), `ui/splash/SplashScreen.kt` + `AuthViewModel` (session routing), `ui/navigation/AppNavigation.kt` (`Screen` sealed class), `data/health/HealthConnectManager.kt` (can it read height/weight?).
- TrackMe (design reference): its onboarding flow collects **unit preference (kg/lbs) + fitness goal** in a short multi‑step wizard — reuse the *shape*, expand the fields.
- HealthifyMe reference ([`01`](01-APP-FEATURE-INVENTORY.md)): onboarding collects sex/age/height/weight/activity level + goal (lose/maintain/gain + target rate) → the exact inputs for the BMR/TDEE budget.

## LEARN
- How the app routes after auth today (`SplashScreen` → Dashboard/Login) so onboarding can slot in as a **gated destination** between successful sign‑in and the Dashboard.
- The `ProfileRepository` upsert path + `profiles` table shape, so extending the model is a schema + mapper change (with a P0 migration), not a rewrite.
- Which fields Health Connect can pre‑fill (height, weight/body composition) to reduce typing.
- The exact inputs each downstream formula needs: **Mifflin‑St Jeor** needs sex, age, height (cm), weight (kg); TDEE needs an **activity multiplier** (sedentary→very active); the calorie budget needs **goal type + target rate**.

## EXECUTE (small, ordered steps)
1. **Model:** extend `UserProfile` with `gender` (enum: female/male/other/preferNotToSay), `birthDate` (or `age`), `heightCm`, `weightKg`, `activityLevel` (enum), `goalType` (lose/maintain/gain), optional `targetWeightKg` + `targetRateKgPerWeek`, `unitPreference` (metric/imperial), and a `hasOnboarded: Boolean`. Keep `displayName`. Add derived helpers `ageYears()` and (later, in P6) BMR/TDEE use cases read these.
2. **Persistence:** add the new columns to the Room `profiles`/user cache (real migration per P0) + extend the Supabase `profiles` DTO/table + mapper. Offline‑first: write to Room first, sync via the P0 `SyncManager`.
3. **Onboarding gate:** after a successful sign‑in/sign‑up, if `profile == null || !hasOnboarded` → route to `Onboarding` instead of `Dashboard`; else straight to Dashboard. Add an `Onboarding` route to the `Screen` sealed class.
4. **Wizard UI** (Compose, in HelloHealth's gradient/`FrostedCard` style — a short, skippable‑where‑safe multi‑step flow with a progress indicator):
   - Step 1 — Basics: display name (pre‑filled from Google), gender, birth date, unit preference.
   - Step 2 — Body: height, current weight (pre‑fill from Health Connect when available; allow manual).
   - Step 3 — Activity & goal: activity level, goal type, optional target weight + rate.
   - Step 4 — Confirm: show the derived daily calorie budget + suggested `ActivityGoals`; let the user accept or tweak. Seed `ActivityGoals` from goal/activity.
5. **Persist + finish:** upsert the profile with `hasOnboarded = true`, seed goals, navigate to Dashboard (pop onboarding off the back stack).
6. **Edit later:** wire the existing Profile/Goals editors to change any of these values after onboarding (re‑derives the budget). No re‑onboarding for returning users.
7. **Validation & fallbacks:** sensible ranges (age 13–120, height/weight bounds), required vs optional fields, and a defined path if the user backs out (persist partial + resume, or allow "finish later" with safe defaults so the app still functions).

## TEST
- Unit: profile mapper (domain↔DTO↔entity) round‑trip incl. new fields; `ageYears()` from birth date; the onboarding‑gate decision (`null`/`!hasOnboarded` → onboarding; else dashboard); input validation ranges.
- Migration test: old profile rows (only `displayName`) migrate without data loss and read back with null/default new fields.
- Manual: new account → onboarding shown once → Dashboard; sign out / sign in again → **no** onboarding; Health Connect pre‑fill populates height/weight when granted; editing profile re‑derives the budget.
- Offline: complete onboarding with no network → persists to Room, syncs later (no lost writes).

## REVISE / notes
- This directly **fixes** the inherited BMR age‑25/male hardcode: P5/P6 read `UserProfile` instead.
- Keep gender inclusive (`other`/`preferNotToSay`); when sex is needed for Mifflin‑St Jeor and unset, fall back to a documented neutral estimate rather than crashing.
- Don't block the whole app if onboarding is abandoned — degrade to safe defaults and surface a "complete your profile for accurate calories" nudge (ties into P7 coaching).
