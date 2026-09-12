# 03 — Unified Architecture

How the four big features become ONE app: dashboard cards, one data model, the
cross‑feature data flows that create user value, the backend/sync/logging/exception
strategy, the dynamic mood theme, and gamification.

---

## 1. Dashboard cards

Keep HelloHealth's card‑based dashboard. Cards (top to bottom):

| Card | Source | Purpose |
|---|---|---|
| **Daily Wellness Score** (hero) | New (cross‑feature) | One 0–100 "how's today going" number fusing activity, nutrition, recovery, mood + a one‑line narrative. Anchors gamification. *(P7)* |
| **Today's Activity** (steps ring) | HelloHealth (keep as‑is) | Steps ring + Active Burn + Active Time. Becomes the canonical **calories‑out (activity)** source for Nutrition. |
| **Workout / Training** | TrackMe | Today's planned day or "Start Session", logging streak, last session, readiness chip. Publishes lifting calories + muscle stimulus. *(P4)* |
| **Nutrition / Calories** | HealthifyMe‑style (new) | Calorie ring (consumed vs **adjusted budget**) + macro bars (P/C/F/fibre) + water. CTAs: search, barcode, quick‑add, water. *(P6)* |
| **Emotions / Mood** | MyEmotions | Latest mood emoji + label + confidence, today's dominant mood, Scan/Log CTA. **Drives the app‑wide theme tint.** *(P1/P2)* |
| **Vitals & Recovery** | HealthSync intent + HC | RHR, HRV, SpO2, BP, glucose, respiratory rate, sleep, VO2max + unified Readiness score. *(P3)* |

---

## 2. Unified data model

### Module layout
Stay **single‑module `:app`** (matches HelloHealth). Organize by feature sub‑package under `com.hellohealth`:

```
domain/model/{activity,workout,emotion,nutrition,vitals,gamification}
domain/repository            domain/usecase/<feature>
data/health                  data/local/{dao,entities}   ← finally ACTIVATED
data/remote/<feature>        data/repository
data/analytics               ← ported TrackMe engines
data/ml                      ← ported MyEmotions MTCNN + PyTorch
ui/<feature>                 ui/dashboard/components      ← one card composable per feature
ui/theme
```
*If build times balloon later, split `:core` / `:analytics` / `:ml` as a phase‑2 refactor — do **not** start multi‑module.*

### Shared keys (makes cross‑feature joins trivial)
- One canonical `DayKey(userId: String, localDate: String /* ISO yyyy-MM-dd, device tz */)` value object used by every day‑bucketed feature. Mirrors HelloHealth's existing `daily_health_snapshots` PK `(user_id, snapshot_date)`.
- Event rows keyed `(userId, id: UUID)`.
- Every timestamp carries **UTC epochMillis + a local tz‑offset** (HealthSync rule) so day‑bucketing survives travel/DST.

### Room (finally the offline‑first store — not dead code)
Bump `AppDatabase`, **replace `fallbackToDestructiveMigration` with real tested migrations**. Every syncable table carries `(userId, updatedAt epochMillis, deletedAt nullable, isSynced Boolean)` for LWW + tombstones. Tables by feature:

- **existing → Room‑backed:** `daily_health_snapshots` (cache of `HealthSummary` incl. vitals JSON), `activity_goals`, `food_preferences`, `profiles`.
- **emotion:** `emotion_records` (`id,userId,timestampUtc,tzOffset,emotion,confidence,source,imageUri,visibility,note,synced`).
- **workout:** `workout_plans / workout_days / planned_exercises / workout_sessions / session_sets / personal_records` + 4 analytics caches `muscle_weekly_analytics / exercise_progress_snapshots / daily_health_analytics / body_state_snapshots`. Exercises catalog seeded from ported `exercises.json`.
- **nutrition:** `food_items` (cached USDA/OFF foods), `food_entries` (date, meal, foodItemRef, qty, resolved kcal+macros, method), `water_logs`, `daily_nutrition_summary` (**the shared calories‑in record**).
- **vitals:** `vitals_samples` (HealthSync canonical record: content‑hash id, type tag, value, metadata JSON — one table for the ~20 vitals types beyond what `HealthSummary` holds).
- **gamification:** `streaks`, `points_ledger`, `achievements`.
- **cross‑feature read model:** extend `daily_health_snapshots` (or a `day_rollup`) that the Wellness card + coaching consume.

### Domain reuse
`HealthSummary` stays the central daily aggregate and is **extended** (not replaced) with missing HealthSync vitals (respiratoryRate, bodyTemperature, restingHeartRate, hydration; it already has SpO2/BP/glucose/VO2max/sleep). Add `EmotionRecord`, `FoodEntry/FoodItem/DailyNutritionSummary/WaterLog`, and reuse TrackMe's analytics models under `domain/model/workout`.

### Supabase mirror
One table per Room table, all under RLS `auth.uid() = user_id`, snake_case DTOs (extend HelloHealth's DTO↔domain mapper style). Provision via a checked‑in `run_sql_in_supabase.sql` (port TrackMe's). `daily_health_snapshots` keeps its `(user_id, snapshot_date)` upsert PK.

---

## 3. Cross‑feature data flows (the reason it's ONE app)

These are the concrete links that make the whole worth more than the parts. Each degrades gracefully when a source pillar is missing.

| From | To | Data | User value |
|---|---|---|---|
| Today's Activity + Workout | **Nutrition** | calories‑out = active calories (HC) + lifting calories (engine TDEE component) | Nutrition ring shows **adjusted budget = base + burned − eaten** — HealthifyMe's calories‑in‑vs‑out. "Earned food" is the single most motivating number. |
| **Nutrition** | Vitals/Recovery + Workout | calories‑in, protein g, hydration, net balance | Under‑eating protein or a big deficit lowers recommended training volume and flags "under‑fuelled." |
| **Vitals** (HRV/RHR/sleep/SpO2) | Workout | unified Readiness score (ported `ReadinessScoreCalculator`) | Workout card recommends today's intensity: high → "go heavy / add a set", low → "deload / active recovery." |
| **Emotions** | Workout + Nutrition | latest/dominant emotion label + valence | Mood‑aware nudges: low‑valence → "a short walk boosts mood" + gentler food; high stress + poor sleep suppresses hard‑workout prompts. |
| Workout (fatigue + last session) + Vitals (sleep) | **Emotions** | training load, recovery hours, sleep debt | Emotion insights gain context: "low‑mood days cluster with poor sleep + high fatigue." |
| Emotions + Nutrition + Activity + Recovery | **Wellness Score + Gamification** | mood valence, nutrition adherence %, goal progress, readiness | One composite number + streaks/points reward **balanced** days, not just steps. |
| **Vitals** | Nutrition (budget) | BMR/TDEE inputs (weight/height + steps + active cal) | Calorie budget derives from **measured** expenditure (Mifflin‑St Jeor + measured activity), self‑adjusting to how active the user actually was. |
| Workout + Nutrition + Mood | **Weekly Insights** | weekly volume, protein/calorie adherence, mood trend | Extends `BuildWeeklyInsightsUseCase` into cross‑domain coaching cards ("hit protein 6/7 and PR'd — momentum is real"). |

Implementation: a thin `DailyRollupRepository` (or extended snapshot repo) is the shared read model; producers write their day record, consumers read the rollup — features stay decoupled.

---

## 4. Backend / sync / logging / exception strategy

- **Unified Supabase schema:** one table per Room table under RLS `auth.uid() = user_id`; checked‑in DDL; snake_case DTOs with `@SerialName`.
- **Offline‑first via Room:** every user write → Room first (`isSynced=false`), UI reads Room Flows → the app is fully usable offline. Fixes HelloHealth's fire‑and‑forget weakness and MyEmotions' insert‑only fetch.
- **Sync engine (port TrackMe `SyncManager`):** WorkManager‑based bidirectional **LWW** reconciler (add WorkManager + hilt‑work — currently absent in HelloHealth). Order: push tombstones first, then merge in strict FK order (plans→days→planned_exercises→sessions→sets→PRs, then prefs, analytics caches push‑mostly, nutrition, emotions, vitals). Triggers: debounced one‑shot (~5s, coalesced) after writes + periodic 15‑min job + a foreground initial sync on login with a timeout. Health Connect stays source‑of‑truth for activity/vitals (read‑through cache into Room, then upsert to Supabase).
- **Conflict resolution:** generic LWW by `updatedAt`; PRs LWW by `achievedAt`; day‑aggregate rows are recomputable so prefer the row with **more complete** data (reuse `completionLevel()`/PARTIAL‑vs‑COMPLETE tie‑break). Idempotency for vitals/emotions via a deterministic **content‑hash id** excluding wall‑clock (HealthSync rule) — re‑syncs never duplicate.
- **Logging:** one `AppLogger` (Timber‑style) with per‑feature tags + a `sync_log` table recording per‑run pushed/pulled/conflicts/failures, surfaced in a hidden debug screen. ML inference, HC reads, and repo fallbacks all log at defined levels.
- **Defensive exception handling — nothing crashes, everything has a defined fallback:**
  - Health Connect: keep the existing nullable‑guard degrade‑gracefully pattern.
  - Supabase: `runCatching` → on failure keep Room data, mark `isSynced=false`, retry next cycle; never surface a crash.
  - ML pipeline: MTCNN "no face" → `Result.failure` with a user message; model‑load failure → disable scan, allow manual mood only.
  - Food APIs: timeout → fall back to cached `food_items` or quick‑add.
  - Maps/location: permission denied → hide map.
  - Every ViewModel `UiState` has explicit Loading/Empty/Error branches → a failed dependency downgrades a **card**, not the app. Repos return sealed `Result`; the sync worker returns `Result.retry()`, never a `.failure()` that drops data.
- **Release gates:** enable R8/minify + real Room migrations before release (closes HelloHealth's two known release‑quality gaps).

---

## 5. Dynamic mood theme (keep the look, tint by emotion)

**Preserve HelloHealth's gradient/modern theme wholesale.** Do NOT adopt MyEmotions' full theme swap. Port only the *concept* as an accent‑**tint** layer. Full detail in [`08-FEATURE-PLAN-gamification-theme.md`](08-FEATURE-PLAN-gamification-theme.md). In short:

1. `MoodAccent` registry (port MyEmotions `Color.kt`/`MoodTheme.kt` values): each of the 8 labels (+ manual Calm/Neutral) → one accent `Color` + a 2–3 stop gradient. **Neutral = HelloHealth's own green** (the default when tint is off or no emotion yet).
2. Keep the static light/dark `colorScheme` as the **structural** theme — surfaces, text, Material3 roles never move, so contrast/readability never depend on mood.
3. Expose one `CompositionLocal LocalMoodAccent` (Color + gradient) at the theme root. Refactor the signature gradient idioms (vertical backgrounds, "Health" wordmark, `FrostedCard`) to read it via `lerp(primary, moodAccent, ~0.35f)` — same look, hue shifts with mood. Per‑feature chart/ring hexes stay independent.
4. A `ThemeHolder`/small ViewModel above `AppNavigation` computes `currentAccent = combine(latest EmotionRecord today, settings.isDynamicTheme)`; `MainActivity` provides it via `CompositionLocalProvider` around `HelloHealthTheme`.
5. Wrap the accent in `animateColorAsState(tween(800–1000ms))` (as MyEmotions does) → smooth re‑tint when a new mood is logged. Dark mode keeps dark surfaces but still animates the accent.
6. Settings toggle (`isDynamicTheme`, DataStore, default **on**). Off / no emotion → green (identical to today).

---

## 6. Gamification

A cross‑feature engine (`domain/usecase/gamification` + `streaks / points_ledger / achievements` tables, keyed by `DayKey`, synced LWW). Three mechanics:

1. **Streaks** — per‑pillar and composite, using the **same consecutive‑day walk‑back** algorithm already proven in both MyEmotions (`loggingStreak`) and TrackMe (workout streak): count distinct sorted‑desc dates while contiguous, anchored to today/yesterday. Track: activity goal hit, workout logged, mood logged, nutrition logged, and a **"Balanced Day"** composite (all pillars touched).
2. **Points** — append‑only `points_ledger`; each pillar emits points on defined events (goal hit, session finished + volume, protein target met, mood logged, readiness acted‑on) → auditable + recomputable; daily/weekly totals feed the Wellness card.
3. **Achievements/badges** — threshold rules over the ledger + streaks (7‑day balanced streak, first PR, protein 6/7, mood 30 days), evaluated by a single `AchievementEvaluator` after each sync/rollup.

Engagement loop: the Wellness card shows today's points + active streaks + next‑badge progress; real daily reminder notifications (fixing MyEmotions' 1‑min demo with a proper periodic WorkManager) nudge the **weakest** pillar. All rules deterministic, defensively skip missing pillars, computed from Room (offline‑first) then synced — never blocking the UI.
