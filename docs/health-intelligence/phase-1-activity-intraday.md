# Phase 1 — Activity truth + intraday foundation + time-of-day charts

> **STATUS: COMPLETE** (on `feature/health-intelligence`, on-device verified on 74ed1bb5, not yet
> merged/pushed). Key on-device findings baked back into later phases:
> - This device **does** feed continuous intraday HR to Health Connect (154 five-min buckets/day) →
>   Phases 3/4 (cardio-load, strain, recovery, stress) can be **measured**, not just estimated.
> - Samsung writes **no** `ActiveCaloriesBurnedRecord` and only a **daily** `StepsRecord` (no real
>   hourly shape) → active calories is reconstructed (ACSM MET), active minutes come from HR-elevated
>   time, and the hourly steps/calories graphs honestly show empty states on this source.
> - Active-minutes HR threshold calibrated on-device: 90 bpm floor / resting+25 (reproduced ~95 min vs
>   Samsung's ~100). Active burn ~365 vs Samsung 374; active time 95 vs 100; total burn ~1,746.

**Goal:** make today's activity numbers correct and lay the intraday time-series substrate everything
later depends on. Fix the three activity bugs; add Health Connect intraday reads (steps/hour,
active-cal/hour, 5-min HR buckets); persist them in a new **device-local** `intraday_series` Room table
(v15→16); ship **BarChart** + **TimeOfDayChart** primitives; make login/logout data-safe and partial-day
rollups reconcilable.

**Depends on:** nothing. **Outcome:** correct active minutes / total burn on the dashboard, plus
steps/calories/heart-rate "through the day" graphs. This phase builds the HC-intraday-read +
time-axis-chart + intraday-storage patterns that Phases 2–4 reuse verbatim.

Reuse: the defensive try/catch-to-empty read style, the deterministic-id upsert idempotency
(`"$userId|rollup|$localDate"`), `persistVitalsRollup` as the sibling-write template, `drawTrend` math
conventions, `SelectedDateHolder` for the active day, the `GoalsViewModelTest` VM-test pattern.

## Stages

### Stage 0 — baseline
New author-pinned branch off master (`feature/health-intelligence`). Confirm `./gradlew
:app:testDebugUnitTest` green; confirm v15 is live (`app/schemas/.../15.json`). Device: screenshot today's
active-minutes + total-burn as the "before". No code.

### Stage 1 — total-burn fix (no schema)
`HealthConnectManager.kt:~206`: reorder so `bmr` resolves first, then extract a pure
`resolveTotalCalories(hcTotal, active, bmr) = hcTotal ?: (active + bmr)`; replace the `?: activeCalories`
fallback. Keep the active-calories-honesty comment (`:211-216`) intact — this fixes **total** only.
- Tests: JUnit beside `HealthConnectManagerBmrSelectionTest` — (a) HC total present → HC total; (b) HC
  total null, active=X, bmr=Y → X+Y.
- Device: zero-activity day shows total ≈ BMR, not 0.

### Stage 2 — new device-local `intraday_series` table (v15→16)
Decision recorded in the migration KDoc: **not** reusing `vitals_samples kind='sample'` (vitals-shaped
columns vs typed metric buckets; keeps the readiness `kind='rollup'` path untouched).

New `IntradaySeriesEntity` (feature columns first, the 4 Syncable sync-meta columns LAST per the
MIGRATION_9_10 convention):
- `id: String` — deterministic PK `"$userId|$metric|$localDate|$bucketStartUtcEpochMs"` (re-reads upsert
  the same bucket).
- `userId`, `localDate` (ISO), `metric: String` (`steps|active_cal|hr`), `bucketStartUtcEpochMs: Long`,
  `bucketMinutes: Int` (60 for steps/cal; 5 for HR), `tzOffsetMinutes: Int`, `value: Double`, then
  `updatedAtEpochMs`, `updatedAtTzOffsetMinutes`, `deletedAtEpochMs`, `isSynced`.
- Indices: `(userId, localDate, metric)` + `(isSynced)`.

`IntradaySeriesDao`: `observeSeriesForDay(userId, localDate, metric)` ascending + tombstone-filtered;
`upsert`/`upsertAll`; `getUnsynced`; version-exact `markSynced`.

`MIGRATION_15_16` (CREATE TABLE + indices) registered in `DatabaseModule`; `AppDatabase` → v16 + new
entity/DAO; export `16.json`; `MigrationTest` seeding a v15 row to prove existing data survives. **No
Supabase table** (device-local).
- Device: upgrade-over-v15 install launches without a Room exception; existing data intact.

### Stage 3 — intraday HC reads
Permissions already granted (Steps / ActiveCalories / HeartRate) — no manifest change. New
`fetchIntradaySeries(date): IntradayDay`, separate from `fetchHealthSummary` so a failure never regresses
the summary:
- steps/hour + active-cal/hour via `aggregateGroupByDuration` (1 h bucket) over the existing day window.
- HR via `HeartRateRecord` samples (reuse the per-workout `.flatMap { it.samples }` shape at `:355-363`)
  **downsampled** to 5-min buckets (average), bounded ~288 rows/day.
- Degrade: no HR samples → empty HR series.
- Tests: pure `bucketHeartRate(samples, bucketMinutes)` — empty→empty; a sample on a bucket boundary lands
  in the correct bucket.
- Device: **settle the highest risk here** — verify per-hour steps vs the native app AND whether this
  device feeds intraday HR to HC, before investing in charts.

### Stage 4 — persist intraday (device-local) + partial-day reconciliation
In `ActivityRepositoryImpl`, alongside `persistVitalsRollup`, add `persistIntradaySeries(date,
intradayDay)` upserting bucket rows (idempotent by deterministic id — re-reads replace, never duplicate).

**Smart partial-day reconciliation (user requirement):** today's snapshot is already `PARTIAL` +
today-guarded; make intraday the same — every refresh re-reads today's full intraday window from HC and
upserts by deterministic bucket id, so a morning partial + an evening refresh converge to the correct full
day with no double-count; the rollup/scores recompute from the complete stored intraday each refresh. No
syncer, no Supabase.
- Tests: upsert idempotency (same bucket id twice → one row, latest value); a partial-then-complete day
  converges to the full day.

### Stage 5 — real active-minutes fix
Replace `HealthConnectManager.kt:238-240` + the duplicate at `:307-309` with a pure
`deriveActiveMinutes(sessions, intradayBuckets, estimateFallback)`:
1. Sum `ExerciseSession.durationMinutes` (authoritative).
2. Non-exercise active minutes from intraday buckets — a bucket counts when steps ≥ a named cadence
   threshold **or** active-cal > 0; de-duplicate minutes already inside a session window. **Remove the 60
   cap.**
3. Degrade — no intraday → sessions only; nothing → the old `steps/100` proxy *without* the cap, flagged
   `isEstimate=true`.

The cadence threshold is a documented named constant (calibration risk — see below).
- Tests: exercise-only; intraday present; overlap de-dup; the Samsung-100-min case now returns 100 not 60;
  no-data estimate path sets `isEstimate=true`.

### Stage 5b — login/logout data-safety (user requirement)
Confirm/guarantee `signOut()` never clears Room (it currently doesn't — `AuthRepositoryImpl.kt:66-74` only
drops the Supabase session). Add a guard/test so no future logout path calls `clearAllTables`/row-deletes;
ensure re-login with the same account re-attaches the same `userId` to existing snapshot/vitals/intraday
rows (all `userId`-keyed). If any sign-out path is found to wipe data, fix it to retain local data and only
clear the session.
- Tests: a VM/repository test asserting local rows survive a sign-out → sign-in cycle for the same userId.

### Stage 6 — BarChart primitive
New `ui/charts/BarChart.kt`, Canvas-based, reusing `drawTrend` conventions (index-x, 12% y-pad) with
rounded-top bars; single series, one y-axis, direct value label on the max bar + axis min/max,
`chartSeries*()` colors, dark-mode via those composables. dataviz-gated (run `validate_palette.js` if any
new hue — none expected).
- Device: steps-by-hour as bars; light + dark; empty / single-bar / all-zero cases.

### Stage 7 — TimeOfDayChart primitive
New `ui/charts/TimeOfDayChart.kt`: x-axis is wall-clock time (ticks 00:00 / 06:00 / 12:00 / 18:00 /
24:00); points `(bucketStartUtcEpochMs, value)` mapped by time-of-day fraction; reuse `TrendLine`
gradient + reveal style; gaps render as gaps (no interpolation across missing hours). Explicitly reused by
Phase 2's hypnogram and Phase 3/4's intraday curves.
- Tests: pure `timeOfDayFraction(epochMs, zone)` — midnight→0, noon→0.5, boundary handling.
- Device: intraday HR on a wearable device; tick + gap handling; light + dark.

### Stage 8 — surface the graphs
`ui/health/HealthScreen.kt` (and/or `ActivityDetailScreen`): three cards — "Steps through the day"
(BarChart), "Calories through the day" (BarChart), "Heart rate through the day" (TimeOfDayChart) — fed by a
new VM flow reading `intraday_series` for the `SelectedDateHolder` day; each card has its own empty state.
- Tests: VM test (StandardTestDispatcher + fake DAO) mapping rows → chart points + empty-day.
- Device: end-to-end — corrected active-min + total-burn (S1/S5) + three graphs; past-day via the date
  selector re-anchors; a no-wearable-HR day degrades cleanly.

### Stage 9 — close-out
Full `testDebugUnitTest` + migration test green; before/after screenshots; dataviz validation on both
primitives; no new deps; branch ready. **No merge/push without explicit authorization.**

## Risks

- **Device intraday-HR availability (highest)** — settled empirically in Stage 3 before chart work; HR
  series optional throughout.
- **Active-minutes threshold calibration** — affects every day + downstream rollups; named constant,
  calibrate against a trusted native value.
- **Battery/perf** — intraday only for today + on-demand for a selected past day; cache the window; full
  audit in Phase 7.
- **Backfill** — intraday forward-only (HC history reads are heavy); decide per phase.
- **Serialization drift** — every new field gets a round-trip + "non-null when data" test (anti
  `deepSleepMinutes`-null regression).

## Verification (end-to-end)

- `:app:compileDebugKotlin` clean + `:app:testDebugUnitTest` green (new pure-calculator + VM + migration +
  intraday tests included); author verified on each commit.
- Device `74ed1bb5`: before/after activity card (active-min uncapped, total = active+BMR); three intraday
  graphs render for today, re-anchor on a past day, degrade on a no-HR day; upgrade-over-v15 install keeps
  data; sign-out → sign-in keeps local data.
- Cross-check corrected numbers against the phone's native health app for the same day (sanity).

## Critical files

- `app/src/main/java/com/hellohealth/data/health/HealthConnectManager.kt` — bugs at `:206`, `:238-240`,
  `:307-309`; new intraday reads.
- `app/src/main/java/com/hellohealth/data/local/Migrations.kt` (new `MIGRATION_15_16`) +
  `app/src/main/java/com/hellohealth/data/local/AppDatabase.kt` (v16, new entity/DAO) + `DatabaseModule`.
- New `app/src/main/java/com/hellohealth/data/local/entities/IntradaySeriesEntity.kt` +
  `.../dao/IntradaySeriesDao.kt`.
- `app/src/main/java/com/hellohealth/data/repository/ActivityRepositoryImpl.kt` — persist intraday
  alongside `persistVitalsRollup`; the active-minutes derivation call sites.
- `app/src/main/java/com/hellohealth/data/repository/AuthRepositoryImpl.kt` — logout data-safety guard.
- New `app/src/main/java/com/hellohealth/ui/charts/BarChart.kt` + `.../TimeOfDayChart.kt` (extend
  `TrendCharts.kt` conventions).
- `app/src/main/java/com/hellohealth/ui/health/HealthScreen.kt` — surface the three intraday graphs;
  `SleepSection` (`:266`) is the Phase-2 entry point.
