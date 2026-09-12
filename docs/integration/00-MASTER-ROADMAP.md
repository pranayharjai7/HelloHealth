# HelloHealth — Master Integration Roadmap

> **Goal:** Turn HelloHealth into ONE unified health app that absorbs the best of
> TrackMe (gym), MyEmotions (emotion detection + mood theming), a new
> HealthifyMe‑style nutrition/calorie tracker, and the HealthSync vitals vision —
> as native features (not app‑within‑app), sharing data across cards to create
> real user value, while keeping HelloHealth's existing modern gradient theme.
>
> **Platform (for now):** Android‑only, Kotlin + Jetpack Compose (everything
> HelloHealth already uses). No Flutter port, no iOS, Wear OS deferred.

This folder is the source of truth for the integration. Read the docs in order:

| # | Doc | What it covers |
|---|-----|----------------|
| 00 | **This file** | The phase plan (P0–P7), how to execute one phase at a time, guardrails. |
| 01 | [`01-APP-FEATURE-INVENTORY.md`](01-APP-FEATURE-INVENTORY.md) | Every feature of every source app + what is *actually* implemented vs stubbed, and which assets to reuse. |
| 02 | [`02-TRACKME-KOTLIN-VS-FLUTTER-DIFF.md`](02-TRACKME-KOTLIN-VS-FLUTTER-DIFF.md) | Feature‑by‑feature diff of the two TrackMe apps + which to port from. |
| 03 | [`03-UNIFIED-ARCHITECTURE.md`](03-UNIFIED-ARCHITECTURE.md) | Dashboard cards, unified data model, cross‑feature data flows, backend/sync/logging/exception strategy, dynamic mood theme, gamification. |
| 04 | [`04-FEATURE-PLAN-workout.md`](04-FEATURE-PLAN-workout.md) | Workout card — read‑learn‑execute‑test‑revise. |
| 05 | [`05-FEATURE-PLAN-emotions.md`](05-FEATURE-PLAN-emotions.md) | Emotions card + mood theme + on‑device ML. |
| 06 | [`06-FEATURE-PLAN-nutrition.md`](06-FEATURE-PLAN-nutrition.md) | Nutrition/Calorie card (HealthifyMe‑style). |
| 07 | [`07-FEATURE-PLAN-vitals.md`](07-FEATURE-PLAN-vitals.md) | Vitals & Recovery card (HealthSync intent). |
| 08 | [`08-FEATURE-PLAN-gamification-theme.md`](08-FEATURE-PLAN-gamification-theme.md) | Wellness score, gamification, dynamic mood theme details. |
| 09 | [`09-FEATURE-PLAN-onboarding-profile.md`](09-FEATURE-PLAN-onboarding-profile.md) | New‑account onboarding — capture gender/age/weight/height/activity/goals; extend the profile model that BMR/TDEE, readiness & analytics depend on. |

---

## The starting point (verified against the code, not the READMEs)

HelloHealth today is a clean, single‑module Kotlin/Compose app:

- **Clean‑ish architecture:** `domain/` (models, repo interfaces, 1 use case) → `data/`
  (Health Connect, Room, repo impls) → `ui/` (per‑feature screen + `@HiltViewModel` + `UiState`).
- **Navigation:** single `MainActivity`, `AppNavigation.kt` with a sealed `Screen` class. Dashboard is card‑based (currently one card: Activity Overview).
- **Backend:** Supabase (gotrue auth + Postgrest). Google Sign‑In via Credential Manager.
- **Health:** `HealthConnectManager` provided **nullable** and null‑guarded everywhere → the app degrades gracefully when Health Connect is missing. Health Connect is treated as source of truth for activity.

Three facts that shape the whole plan (all confirmed in source):

1. **Room is dead code.** `AppDatabase` has only `UserEntity`, is unused by any repository, and uses `fallbackToDestructiveMigration`. The real store is Supabase, written **fire‑and‑forget** in `runCatching` (silently drops writes on network failure). → **P0 fixes this first.**
2. **Theme is static.** `HelloHealthTheme` picks a fixed light/dark scheme; there is no dynamic color despite the README. The signature look = vertical gradient backgrounds, a gradient "Health" wordmark, and `FrostedCard`. → mood theming is a *new, self‑contained* accent‑tint layer that keeps this look.
3. **HealthSync has no code.** It is a design/research folder. "Integrating HealthSync" = implementing its **intended vitals coverage + canonical‑record/idempotency rules** by broadening Health Connect reads. Some Samsung metrics (stress, energy score, skin temp) are **permanently unavailable** via Health Connect — documented as a non‑goal.

---

## Execution principle: small, ordered, independently shippable

Each phase below is designed to **build, run, and ship on its own** without breaking the app. Never take on two big features at once. For every phase:

1. Open the relevant `04–08` feature plan and follow **Read → Learn → Execute → Test → Revise**.
2. Keep the change behind the existing UI until it builds and its tests pass.
3. Update this roadmap's checkboxes and note any deviation.

### Phases

- [ ] **P0 — Foundation & offline‑first spine** *(no visible feature change)*
  Activate Room as the offline‑first store behind existing repos; add `updatedAt/deletedAt/isSynced` columns; add WorkManager + hilt‑work; port a generic **LWW + tombstone** `SyncManager` (from TrackMe) over the *existing* tables; replace fire‑and‑forget Supabase writes with Room‑first + background sync; add `AppLogger` + `sync_log`; introduce a `DayKey(userId, localDate)` value object and UTC‑epoch + tz‑offset timestamps; **replace `fallbackToDestructiveMigration` with a real migration**; check in Supabase DDL (`run_sql_in_supabase.sql`).
  *Depends on:* nothing. *Risk:* touches core repos — guard with the repository unit tests HelloHealth currently lacks.

- [ ] **P0.5 — New‑account onboarding + profile model** *(unblocks BMR/TDEE, readiness, analytics)*
  Extend `UserProfile` (today only `displayName`) with gender, birth date/age, height, weight, activity level, and goal (lose/maintain/gain + target rate/weight); persist to Room + Supabase `profiles` via the P0 spine. Add a one‑time onboarding flow after first sign‑up (gated on a `hasOnboarded` flag): a short multi‑step wizard collecting these vitals, seeding `ActivityGoals`, and optionally pre‑filling from Health Connect (height/weight) when available. Existing/returning users skip it; add an "Edit profile / goals" path to change values later.
  *Depends on:* P0. *Risk:* low; it's mostly new UI + a model/table extension. Every downstream feature that computes BMR/TDEE/readiness reads this instead of hardcoding age‑25/male.

- [ ] **P1 — Mood tint theming + Emotions card (manual only, no ML)**
  Port the `MoodAccent` registry + `LocalMoodAccent` CompositionLocal + animated tint into `HelloHealthTheme`; refactor gradient idioms/`FrostedCard`/wordmark to read the accent (`lerp(primary, accent, ~0.35)`); Settings toggle (default on, Neutral = green). Add Emotions domain model + `emotion_records` table + Supabase mirror + **manual** mood logging + Emotions dashboard card + basic insights (Balance Score/valence formulas). Theme tints from the latest logged emotion.
  *Depends on:* P0. *Risk:* low — purely additive + a theme refactor.

- [ ] **P2 — On‑device emotion ML (face scan)**
  Port MyEmotions assets (`enet_b0_8_va_mtl.ptl`, `pnet/rnet/onet.tflite`, `emotionsLabel.txt`) + `MTCNN.kt` + `EmotionPyTorchClassifier.kt` + `Box.kt` into `data/ml`; add PyTorch Lite + TFLite + CameraX; rewrite `detectEmotion` in HelloHealth style (Dispatchers.IO, `Result`). Capture → detect → save `EmotionRecord(source=camera)` → tint updates.
  *Depends on:* P1. *Risk:* ~18 MB assets + native libs bloat the APK; gate scan behind a model‑load capability check that falls back to manual.

- [ ] **P3 — Vitals & Recovery card + unified Readiness**
  Broaden `HealthConnectManager` reads to the HealthSync type set (RHR, HRV RMSSD, respiratory rate, body temp, hydration; SpO2/BP/glucose/VO2max/sleep already present) with per‑type conversion rules from `DESIGN.md`; extend `HealthSummary` + add a `vitals_samples` table (content‑hash idempotent id); port TrackMe `ReadinessScoreCalculator`; add the Vitals & Recovery card + trends screen.
  *Depends on:* P0. *Risk:* some Samsung/HC types absent on‑device — null‑guard every type; readiness handles missing inputs via weight redistribution.

- [ ] **P4 — Workout module (planner + live session)**
  Port `exercises.json` (873 exercises) + seeding; the workout Room graph (plans/days/planned‑exercises/sessions/sets/PRs); `WorkoutSessionManager` singleton + live session screen + foreground notification; planner screens; use cases. Rewrite wiring to HelloHealth Hilt conventions; strip Wear coupling; add tables to `SyncManager`; add the Workout card.
  *Depends on:* P0. *Risk:* largest feature by volume — keep it self‑contained; **Wear bridge explicitly deferred.**

- [ ] **P5 — Body analytics engine**
  Port TrackMe's `ProgressAnalyticsEngine` (11 modules) + models + 4 analytics‑cache tables (compute off main thread, cache to Room, push‑mostly to Supabase); Progress screen cards reusing HelloHealth's Canvas chart + `ActivityRing` idioms.
  *Depends on:* P4, P3, P0.5 *(profile vitals)*. *Risk:* pure math, easy to lift; main risk is UI perf — keep compute off the main thread. Reads gender/age/weight from `UserProfile` (P0.5) instead of hardcoded age‑25/male.

- [ ] **P6 — Nutrition / Calorie tracker + energy balance**
  Food/nutrition Room tables + Supabase mirror; USDA FDC + Open Food Facts remote sources + barcode (ML Kit/ZXing); food search / quick‑add / water / meal‑category logging; BMR/TDEE + calorie‑budget use cases; `DailyNutritionSummary` as the shared calories‑in record; Nutrition card (ring + macro bars); **wire the flagship contract: adjusted budget = budget + caloriesOut − caloriesIn.**
  *Depends on:* P0, P0.5 *(profile → budget)*, P3, P4. *Risk:* external food APIs (network + rate limits) — cache aggressively + quick‑add offline. Photo "Snap" recognition deferred.

- [ ] **P7 — Wellness score, gamification & cross‑feature coaching**
  Daily Wellness Score aggregate + hero card; streaks/points/achievements engine spanning all pillars; extend `BuildWeeklyInsightsUseCase` into cross‑domain coaching; implement the cross‑feature nudges (readiness→intensity, mood→food/workout, deficit→recovery flag); real daily reminder WorkManager (replacing MyEmotions' demo stub).
  *Depends on:* P2, P3, P5, P6. *Risk:* depends on all pillars — keep every nudge rule‑based and defensively skip missing pillars.

---

## Cross‑cutting guardrails (apply to every phase)

- **Offline‑first, never lose a write.** All user writes hit Room first (`isSynced=false`), UI reads Room Flows, a background worker reconciles with Supabase (LWW + tombstones). Sync worker returns `Result.retry()`, never a `.failure()` that drops data.
- **Nothing crashes — everything has a defined fallback.** Every external boundary (Health Connect, Supabase, ML pipeline, food APIs, maps/location) is wrapped so failure downgrades a card to a defined Loading/Empty/Error placeholder. Repositories return sealed `Result` types.
- **Log everything.** One `AppLogger` (Timber‑style) with per‑feature tags; every sync run writes counts (pushed/pulled/conflicts/failures) to a local `sync_log` surfaced in a hidden debug screen.
- **Keep the theme the user loves.** Only the accent + shared gradient brush are mood‑tinted (`lerp` toward the emotion color). Material3 surface/text roles never move — readability is guaranteed.
- **One app, reused algorithms.** Port the *algorithms and model assets* (MTCNN + PyTorch classifier, TrackMe analytics engines, streak walk‑back) verbatim, but **rewrite the surrounding wiring** to HelloHealth's Hilt/Room/repository conventions.
- **Release‑quality gates before shipping:** real Room migrations (no destructive fallback) and R8/minify enabled.

## Known defects to fix *while porting* (don't inherit them)

- TrackMe & Flutter: BMR ignores age/sex (hardcoded 25/male) → read `UserProfile`; PRs are weight‑only → add Epley estimated‑1RM + max‑reps (from Flutter).
- MyEmotions: destructive Room migration; `imageUri` plumbed but never saved; sync is insert‑only (make bidirectional); reminders are a 1‑minute demo with `cancelAllWork()` over‑cancel; `DetectEmotionUseCase` is dead code (VMs call repo directly); "Calm" label is never produced by the model (only manual/theme).
- HelloHealth: dead Room; fire‑and‑forget Supabase writes; `fallbackToDestructiveMigration`; `isMinifyEnabled=false` on release.
