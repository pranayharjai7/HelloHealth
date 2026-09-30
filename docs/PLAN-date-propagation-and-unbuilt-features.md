# HelloHealth — Plan: Universal Date Propagation + Unbuilt Features

> Status: **planned, not yet executed.** This is the agreed implementation plan for the next round of
> work. Part A (date propagation) is the priority; Part B features are sequenced after it, one at a
> time. See also [FEATURES.md](FEATURES.md) and [DATA-MAP.md](DATA-MAP.md).

## Context

Two bodies of work, from user review of the finished four-app integration:

1. **Universal date propagation (PRIORITY).** Changing the date in the dashboard calendar currently
   updates **only** the Activity card — every other card (Nutrition, Emotions, Vitals, AI Coach,
   Weekly Insights) ignores the selected date and always shows "today". Going to a previous day must
   reflect in **all** cards. This is a cross-cutting architectural gap: `selectedDate` lives only in
   `DashboardViewModel`, while the six sibling card VMs each hardcode `LocalDate.now()`.

2. **Three unbuilt roadmap features** (all approved): live workout session logging (P4 tail), body
   analytics engine (P5), wellness score + gamification (P7).

**User decisions:** every card date-aware; **AI Coach = today-only** (past day shows a neutral
"Viewing {date}" state, no LLM call); Weekly Insights re-anchors its 7-day window to end on the
selected date; Workout Plan stays date-independent (a plan is not per-day). All three unbuilt
features in scope, sequenced after the date work.

Stack: Kotlin/Compose/Hilt, Room **v12**, Supabase, offline-first (Room-first → WorkManager →
Supabase, LWW + tombstones). Guardrails (all phases): feature branch off master; commits use
`git -c user.email=pranayharjai7@gmail.com commit`, verify author each time; stage specific files
only; never stage `local.properties`/`.agents/`/`.claude/`; `--no-ff` merge; push/merge/deploy only
on explicit user authorization; additive-only schema; every external boundary has a defined fallback;
`JAVA_HOME` exported for gradle; server DDL deployed via MCP **before** the client ships (PGRST204).

---

# PART A — Universal date propagation (priority)

## A.0 Mechanism: a Hilt `@Singleton SelectedDateHolder`

A tiny process-scoped domain object holding the selected date as a `StateFlow`, injected into every
date-aware VM. One writer (`DashboardViewModel`); every date-aware VM observes it and switches its
reads with `flatMapLatest`. Chosen over (a) per-VM `setDate()` driven by a `LaunchedEffect` — which
reintroduces manual composable wiring and can't cleanly reach the passed-in `EmotionsViewModel` — and
over (c) a nav-scoped shared VM — which over-couples the siblings. The holder works identically for
the `hiltViewModel()`-inside-screen siblings and the `EmotionsViewModel` built in `AppNavigation`
(Hilt hands out the same singleton either way). Mirrors the existing injectable-singleton precedent
(`CameraPreferences`).

**New `core/date/SelectedDateHolder.kt`:** `@Singleton @Inject constructor()`; a
`MutableStateFlow<LocalDate>` (init `LocalDate.now(systemDefault)`); `set(date)` with a future-date
guard (defense in depth); exposes `selectedDate: StateFlow<LocalDate>`. No `@Provides`. Zone is
`ZoneId.systemDefault()` everywhere (matches all existing sites).

**`DashboardViewModel`:** inject the holder. Keep `DashboardUiState.selectedDate` for the calendar UI
(no churn); make the holder the propagation channel — `selectDate(date)` (`:164`) and `jumpToToday()`
(`:175`) additionally call `selectedDateHolder.set(...)`. Activity/Workout path unchanged.

## A.1 Per-VM refactor — the `flatMapLatest(dateFlow){…}` shape

Each date-aware VM injects `SelectedDateHolder` and rebuilds its `uiState` as
`selectedDate.flatMapLatest { date -> <existing reads keyed on date> }.stateIn(...)`
(`@OptIn(ExperimentalCoroutinesApi::class)`, as `WorkoutPlanViewModel` already does). `flatMapLatest`
cancels stale inner flows on rapid date changes.

- **NutritionViewModel** — replace `private val today` + `combine(...)` (`:85-96`): key
  `observeDaySummary(iso)` and the new `observeCaloriesOutForDay(iso)` on the date; `budgetFlow` stays
  date-independent (profile-derived).
- **VitalsViewModel** — replace `combine(observeReadiness(), observeLatestVitals())` with the new
  `observeReadinessAsOf(date)` + `observeVitalsForDay(iso)`.
- **EmotionsViewModel** (passed-in param) — inject the holder (same singleton). Re-scope only the day
  fields via `observeWindow(day, day)` (reuses existing method); **keep `observeLatest()` for the
  theme tint** unchanged, so browsing history does NOT recolor the app.
- **InsightsPreviewViewModel** — convert the one-shot `refresh()` to a reactive `flatMapLatest` over
  the four per-day reads; the "N of 4 tracked" count reacts to the date.
- **CoachingViewModel** — observe the date; if `date == today` run the existing insight path, else
  emit a neutral past-day state (`isPastDay`, `viewingDate`) and **do not call the LLM**. Add those
  fields to `CoachingUiState`; card renders "Viewing {date} · coaching is for today".
- **WorkoutPlanViewModel** — NO CHANGE (plan is not per-day).

## A.2 Edge cases (explicit)

- **calories-out on a past day:** `observeCaloriesOutForDay` emits `Double?` = **null when no snapshot
  exists** (not `0.0`). Net energy balance → `DASH` and the deficit/surplus chip hidden when null;
  `caloriesIn` still shows. Migrate the single `observeTodayCaloriesOut` caller to the dated method.
- **readiness on a past day:** reuse `BuildCrossInsightsUseCase`'s approach — history strictly before
  the day + that day's rollup as `todayMetric`. Show as-of readiness; INSUFFICIENT_DATA / no rollup →
  establishing / `—`.
- **AI Coach past day:** neutral state, no network (A.1).
- **Weekly Insights re-anchor:** the dashboard *preview card* counts only the selected day. The full
  **Insights screen** re-anchors its 7-day window to END on the selected date: `BuildCrossInsightsUseCase`
  already takes `today`+`zone`; `BuildWeeklyInsightsUseCase` must take `asOf: LocalDate = now()`
  instead of internal `now()` (`:25`); `InsightsViewModel` feeds `selectedDate` as the window end;
  `fetchWeeklyStats()` needs an `asOf`/range variant (or degrade: pull what exists, let the pure
  use-cases filter by `asOf`). Most involved → last commit.
- **back-to-today** resumes live Health Connect (Activity force-refreshes when `date==now`; sibling
  cards re-read Room today rows). **Timezone**: `ZoneId.systemDefault()` + ISO `toString()` +
  `toEpochDay()` consistently. **Loading/empty**: each UiState already has DASH/empty defaults +
  `hasData`; add a loading sentinel only if a flicker appears. **Future guard** kept in both
  `selectDate` and the holder; calendar already disables future cells.

## A.3 Minimal repo additions (reuse-first)

- `ActivityRepository.observeCaloriesOutForDay(localDate): Flow<Double?>` — mirror
  `observeTodayCaloriesOut` (`ActivityRepositoryImpl.kt:155`), parameterized, null on no snapshot.
- `VitalsRepository.observeVitalsForDay(localDate): Flow<LatestVitals?>` — wrap the existing
  day-keyed rollup read (`getRollupForDate` / a one-line `observeRollupForDate` DAO query; same table,
  **no migration**); `observeReadinessAsOf(date): Flow<ReadinessScore?>` — copy `observeReadiness`
  with window `[date-35, date]`; `observeReadiness()` becomes `observeReadinessAsOf(now())`.
- Nutrition / Emotions / Coaching: **no new repo methods**.

## A.4–A.8 Commit sequence (build + test green each)

1. **A.4** `SelectedDateHolder` + `DashboardViewModel` wiring + `observeCaloriesOutForDay` + Nutrition
   refactor + null-safe energy balance. Tests: extend `NutritionViewModelTest` (holder `set(pastDate)`
   re-emits; null kcalOut → DASH net); new `SelectedDateHolderTest` (future guard).
2. **A.5** Emotions refactor (day fields via `observeWindow(day,day)`; latest/tint untouched) + card
   copy. Test: `set(pastDate)` changes `today`/`todayCount`/`dominantToday`, not `latest`.
3. **A.6** Vitals dated reads + refactor. Tests: past day with rollup → chips + as-of readiness;
   without → dashed + INSUFFICIENT_DATA.
4. **A.7** InsightsPreview reactive + Coaching past-day state + `CoachingCard` copy. Tests: count
   reacts to `set`; past day → `isPastDay` and `dailyInsight()` NOT invoked (fake call-count).
5. **A.8** Weekly Insights re-anchor: `BuildWeeklyInsightsUseCase.asOf` param; flow `selectedDate`
   into `InsightsViewModel`; `fetchWeeklyStats` asOf/range (or degrade). Tests: use-case with explicit
   `asOf`; VM window ends on selected date.

**Critical files:** `core/date/SelectedDateHolder.kt` (new); `ui/dashboard/DashboardViewModel.kt`,
`NutritionViewModel.kt`, `VitalsViewModel.kt`, `EmotionsViewModel.kt`, `InsightsPreviewViewModel.kt`,
`CoachingViewModel.kt`; `data/repository/ActivityRepositoryImpl.kt`, `VitalsRepositoryImpl.kt`;
`domain/usecase/BuildWeeklyInsightsUseCase.kt`, `ui/insights/InsightsViewModel.kt`.

---

# PART B — Three unbuilt features (each its own phase, AFTER Part A)

**Ordering:** A → F1 (self-contained) → F2 (needs only the P0.5 profile, present) → F3 (launches on
the 4 existing pillars; richer once F1/F2 add pillars — design the score additive). Room versioning
is the serialization point: **F1 = MIGRATION_12_13, F2 = 13_14, F3 = 14_15** (renumber if reordered).
Each Supabase table MCP-deployed before its client commit.

**Established "add synced entity" pattern (reference once, applied in every feature):** entity
implements `Syncable` (4 sync cols LAST: `updatedAtEpochMs`, `updatedAtTzOffsetMinutes`,
`deletedAtEpochMs`, `isSynced`); DAO (`getUnsynced/getById/upsert/markSynced`, tombstone-filtered
reads); register entity + `version++` + `dao()` in `AppDatabase.kt`; `MIGRATION_x_y` in `Migrations.kt`
(CREATE matching Room's exact column order) + `DatabaseModule.addMigrations` + `MigrationTest` +
exported schema JSON; Syncer (copy `WorkoutPlanSyncer`) + one `@Binds @IntoSet` in `SyncModule`;
Supabase table appended to `run_sql_in_supabase.sql` (CREATE + index + `set_updated_at` trigger + RLS
owner policy + `GRANT SELECT,INSERT,UPDATE` + `NOTIFY pgrst`), MCP-deployed first; new `FeatureTag`.
**Dashboard card pattern:** component in `ui/dashboard/components/` + `@HiltViewModel` + wire in
`DashboardScreen.kt` + `Screen` route + `composable` in `AppNavigation.kt`. **Reuse:** `BodyEnergy`
(bmr/tdee/calorieBudget), `ReadinessScoreCalculator`, `ActivityRing`, the Canvas chart idioms in
`InsightsCrossCharts`/`VitalsTrendsScreen`, the `currentStepStreak` walk-back in
`BuildWeeklyInsightsUseCase:39`.

## Feature 1 — Live workout session logging (P4 tail)

Planning hierarchy is shipped; nothing logs actuals. Add:
- **Entities:** `WorkoutSessionEntity` (`workout_sessions`: id, userId, planId?, dayId?, title?,
  activityType, start/end epochMs, durationSeconds?, status active|completed|abandoned, localDate,
  note?, totalVolumeKg?, caloriesEstimate?) and `SessionSetEntity` (`session_sets`: id, sessionId,
  userId, plannedExerciseId?, exerciseId, orderIndex, setNumber, reps?, weightKg?, durationSeconds?,
  distanceKm?, rpe?, isWarmup, isCompleted, isSkipped, loggedAtEpochMs) — both Syncable. FK anchors:
  `planned_exercises.id` / `exercises.id`.
- **New `WorkoutSessionRepository`** (separate from `WorkoutPlanRepository` — logging ≠ planning):
  start/logSet/editSet/skipSet/deleteSet/finish (stamp end+duration+volume)/abandon/observeActiveSession/
  prefillFromLastSet. Offline-first. Two syncers + binds; two Supabase tables; `MIGRATION_12_13`;
  `FeatureTag.WORKOUT_SESSION`/`SESSION_SET`.
- **State machine / timer / service:** `@Singleton WorkoutSessionController` (idle→activeSet→resting→
  completed `StateFlow`); rest timer persisted to **DataStore** (restore remaining from
  `restEndsAtEpochMs − now` → process-death safe); `WorkoutSessionService` foreground service +
  ongoing notification (manifest + `POST_NOTIFICATIONS`/`FOREGROUND_SERVICE*`).
- **UI/nav:** `ui/workoutsession/ActiveWorkoutScreen.kt` + VM (start-from-planned-day prefills
  `target*`; log/skip/edit/delete; rest timer; finish summary reusing `ActivityRing`); `Screen.
  ActiveWorkout("active_workout?dayId={dayId}")`; entry CTAs on `DayDetailScreen` + dashboard
  `WorkoutPlanCard`.
- **Deferred sub-step:** personal records (Epley 1RM) + `personal_records` table.
- **Commits:** (1) entities+DAOs+MIGRATION_12_13+MigrationTest; (2) Supabase tables (MCP)+2 syncers+
  binds+tags+tests; (3) repository+tests; (4) controller+DataStore+tests (process-death restore);
  (5) ActiveWorkoutScreen+VM+nav+CTAs; (6) foreground service+notification+permission.
- **Risks:** process death mid-rest; Android 14+ FGS policy; single-active-session invariant
  (repo-enforced); edits after finish re-derive volume/duration; nullable planId/dayId for ad-hoc.

## Feature 2 — Body analytics engine (P5)

Body data is "latest only" today; no history. Add:
- **Entity:** `BodyMetricEntity` (`body_metrics`), deterministic id `"$userId|body|$localDate"`
  (vitals-rollup discipline): localDate, timestamp, tzOffset, weightKg?, bodyFatPct?, leanMassKg?,
  waistCm?, vo2max?, source manual|health_connect + 4 sync cols. `MIGRATION_13_14` (additive).
- **Capture:** manual weight-log sheet → `BodyMetricsRepository.logWeight(...)`; HC-daily → extend
  `ActivityRepositoryImpl.fetchSummary` (already persists a vitals rollup at `:190`) to upsert a
  `body_metrics` row when HC returns weight/bodyFat (idempotent, `runCatching`, no-op signed-out).
- **Analytics/UI:** pure `BodyAnalyticsUseCase` (BMI, body-fat %, lean mass = `weight*(1−bodyFat)`,
  weight trend, TDEE via `BodyEnergy.tdee`; `today`/`zone` params). `ui/progress/ProgressScreen.kt`
  + VM (trend charts reusing the Canvas idioms) + `Screen.Progress` + entry point; optional
  date-aware `BodyCard` via the Part-A holder. `BodyMetricSyncer` + bind; Supabase table (MCP);
  `FeatureTag.BODY_METRICS`.
- **Commits:** (1) entity+DAO+MIGRATION_13_14+MigrationTest; (2) Supabase+syncer+bind+tag+test;
  (3) repository (manual + HC-daily)+tests; (4) `BodyAnalyticsUseCase`+tests; (5) ProgressScreen+VM+nav
  (+optional BodyCard).
- **Risks:** sparse history (1 point → value, dash slope); store canonical kg/cm (display via
  `UnitPreference`); HC-vs-manual same day (deterministic id = LWW; prefer manual via `source`);
  BMI needs profile height (null → dash).

## Feature 3 — Wellness score + gamification (P7)

Zero code today; launches on the 4 existing pillars. Add:
- **Pure `WellnessScoreUseCase`** (0–100) fusing activity-goal progress + nutrition adherence (vs
  `BodyEnergy.calorieBudget`) + readiness (`ReadinessScoreCalculator`) + mood valence. **Skip missing
  pillars and renormalize over present ones** (never penalize a missing dimension). `today`/`zone`
  params, no I/O.
- **Entities:** `StreakEntity` (`streaks`, id `"$userId|streak|$pillar"`), `PointsLedgerEntity`
  (`points_ledger`, **append-only**, idempotent per (day,source) via deterministic id),
  `AchievementEntity` (`achievements`, id `"$userId|ach|$code"`). `MIGRATION_14_15` (additive).
- **Calculators:** pure `StreakCalculator` (per-pillar walk-back reusing the `currentStepStreak`
  idiom + composite "Balanced Day"); pure `AchievementEvaluator`. Thin repo, offline-first; recompute
  on dashboard read.
- **UI/reminders:** hero `WellnessCard` (date-aware via the holder) + `ui/wellness/WellnessScreen.kt`
  + VM (pillar breakdown / streaks / achievements) + `Screen.Wellness`; daily reminder WorkManager
  (reuse the `sync` WM infra) + `POST_NOTIFICATIONS`. 3 syncers + binds; 3 Supabase tables (MCP);
  3 FeatureTags.
- **Commits:** (1) pure `WellnessScoreUseCase` + `StreakCalculator` + `AchievementEvaluator` +
  exhaustive tests (skip-missing renormalization, walk-back, balanced-day) — core first, no
  persistence; (2) entities+DAOs+MIGRATION_14_15+MigrationTest; (3) Supabase+3 syncers+binds+tags+
  tests; (4) repository+tests; (5) WellnessCard+WellnessScreen+VMs+nav; (6) reminder WorkManager.
- **Risks:** missing pillars must not drag the score (renormalize — core correctness); streak
  day-boundary (`systemDefault`/`localDate`); append-only ledger idempotent per (day,source) to avoid
  double-award on recompute; one-time achievement unlock guard (`unlockedAtEpochMs != null`);
  retroactive past-day scoring is free (use-case is `asOf`-parameterized — composes with Part A).

---

## Verification (end to end)

- **Per commit:** `./gradlew :app:compileDebugKotlin` clean; `:app:testDebugUnitTest` green (new
  tests included); author verified after each commit.
- **Part A on-device (device 74ed1bb5):** log food + mood + a vitals day; change the calendar to a
  past day → confirm Nutrition (calories/macros/water for that day), Emotions (that day's dominant +
  count, tint unchanged), Vitals (that day's chips + as-of readiness), Insights preview count, and
  the AI Coach neutral "Viewing {date}" (no network — confirm via logcat no `ai-coach` call) all
  reflect the selected day; net energy balance dashes when no snapshot; jump back to Today restores
  live values. Rapid date-flipping shows no stale data.
- **Part B:** each feature's migration validated by `MigrationTest` (12→13→14→15); each Supabase
  table confirmed via `list_tables` + `get_advisors(security)` (no missing-RLS); on-device smoke of
  each new screen; workout session survives process death mid-rest; wellness score renormalizes with
  a missing pillar.
- **Regression:** existing dashboard cards + all prior tests stay green; minified R8 release still
  builds (ML Kit/CameraX + new libs kept).

## Risks / ordering summary

Part A is isolated and low-schema-risk (one new DAO query, no new tables) — do it first and merge
before Part B. Part B features are large and serialize on Room version numbers; build them one at a
time, each merged before the next starts, so migrations never collide. F3's score is the natural
capstone (reads every pillar) but is designed to launch on the four that exist today.
