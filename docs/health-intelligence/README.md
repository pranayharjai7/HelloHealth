# HelloHealth — Health Intelligence Program

> Goal: evolve HelloHealth from a few daily numbers into a full-spectrum health-intelligence app —
> activity, sleep (full staging + score), cardiovascular health, cardio load, strain, recovery, stress,
> and body composition — all inter-related and composed into daily/weekly/monthly insights and
> goal-aware projections. The bar is: **better than Whoop.**

This folder is the living source of truth for the program. The master roadmap lives here; each phase has
its own file. Phase 1 is fully detailed; later phases carry their roadmap paragraph and are expanded to
full detail when reached.

## Why

Cross-checking against Samsung Health on the same phone, the user found:
- **Activity numbers wrong** — e.g. for one day Samsung showed steps 9094, active time 100 min, active
  calories 374, total burned 1476, distance 6.76 km; HelloHealth disagreed on active time, active
  calories, and total burn.
- **Sleep impoverished** — Samsung shows a sleep score, full staging (deep/REM/light/awake), latency,
  per-component ratings, a hypnogram, and in-sleep SpO2 / heart-rate / respiratory graphs. HelloHealth
  showed sleep *duration* only.
- **No intraday graphs** — Samsung plots steps / active time / active calories against time of day.
- **Whole dimensions missing** — cardiovascular health, cardio load, strain, stress, recovery, body
  composition, and the insights/projections that tie them together.

## Approved decisions (fixed)

1. **Delivery**: multiple big phased plans, executed one by one. Each phase is independently shippable.
2. **Derived scores**: build full, **degrade gracefully** — every score emits a best-effort value + an
   explicit `isEstimate`/`quality` flag + reason, rendered as a labelled estimate (never faked or hidden),
   mirroring `ReadinessScoreCalculator`'s `missingDataFlags` shape.
3. **Projections**: **richer, goal-aware** — trend extrapolation + "to hit X by Y, do Z", with a
   confidence band and clear "estimate, not medical advice" labelling.
4. **Intraday storage is DEVICE-LOCAL** (not synced to Supabase — ~300 heart-rate buckets/day/user is too
   costly). Only daily rollups + computed scores sync. Two hard constraints:
   - **Logout/login must be data-safe**: an accidental sign-out must never erase local data; re-login
     re-attaches the same `userId` to existing rows.
   - **Smart partial-day reconciliation**: if a rollup was computed mid-day (PARTIAL) and synced, the rest
     of that day must later be recomputed correctly from stored intraday + a re-read, upserting the same
     day row (idempotent by deterministic id), never double-counting or losing the morning.

## Methodology foundation

Health Connect provides **raw signals only** — sleep score, recovery, strain, cardio load, and stress are
all app-computed. We use documented, reproducible methods:

**Cardio load / strain (from heart rate)**
- Zones via **Karvonen heart-rate reserve**: HRR = HRmax − HRrest; a zone's lower bound = HRrest + pct·HRR.
  HRmax estimated **211 − 0.64·age** (Nes), user-overridable.
- **Edwards' summated-HR-zone TRIMP** = Σ (minutes in zone *i* × *i*), zones 1–5 → multipliers 1–5. The
  day's cardio-load input; needs intraday HR.
- **Banister TRIMP** (per session) = duration · ΔHRr · 0.64·e^(1.92·ΔHRr) [men] / 0.86·e^(1.67·ΔHRr)
  [women], where ΔHRr = (HRavg − HRrest)/(HRmax − HRrest).
- **Strain (0–21)** = logarithmic rescale of the day's accumulated TRIMP onto 0–21, personalized baseline.
- **ACWR** = 7-day EWMA load ÷ 28-day EWMA load; sweet spot ~0.8–1.3, >1.5 flags spike/overreach.

**Recovery / readiness (0–100)** — weighted deviation of this morning's physiology from a 28-day rolling
personal baseline: HRV RMSSD (↑ good, ~40%), resting HR (↓ good, ~25%), respiratory-rate stability (~10%),
prior-night sleep performance (~15%), recent strain/ACWR (~10%). Bands red/yellow/green. Extends the
existing `ReadinessScoreCalculator`.

**Sleep score (0–100) + per-component bands** (AASM / National Sleep Foundation reference ranges) — total
sleep time 7–9 h; stage proportions REM ~20–25%, deep (N3) ~13–23%, light (N1+N2) ~50–60%; sleep
efficiency (asleep ÷ in-bed) >85% good; onset latency <20 min ideal; low WASO/awake. Each component maps to
needs-attention / fair / good / excellent.

**Stress (0–100)** — HRV suppression + elevated HR relative to resting, sampled across the day.

**Projections** — linear/EWMA extrapolation per daily series with a confidence band + goal-aware guidance.
Honest, clearly-labelled estimates. Never medical claims.

## Working rules (every phase)

- **New daily field → 4 serialization points**: `HealthSummary` → `SnapshotJson` (encode + decode) →
  `DailyHealthSnapshotDto` (`SnapshotSyncer`, both directions) → Supabase columns. **New vitals-rollup
  field** → `VitalsSampleEntity` → migration → `VitalsSampleDao` → `VitalsSampleSyncer` DTO → Supabase →
  `VitalsRepositoryImpl` mappers. For every new field add an encode/decode round-trip test **and** a
  "written non-null on a day with data" assertion. (The `deepSleepMinutes`-always-null bug is exactly a
  half-applied plumbing — fail CI on it.)
- **Room version bump** → entity + `MIGRATION_n_(n+1)` in `Migrations.kt` + register in `DatabaseModule` +
  bump `AppDatabase.version` + export `app/schemas/.../(n+1).json` + a `MigrationTest`
  (`runMigrationsAndValidate`, seeding an old row to prove data survives).
- **Every chart follows the `dataviz` skill**: `ChartPalette`/`chartSeries1-3()` only, one y-axis, legend +
  direct labels (never color-alone), CVD-safe, light + dark; run `validate_palette.js` on any new hue.
- **No new dependencies** unless justified in-stage. Author-pinned commits
  (`git -c user.email=pranayharjai7@gmail.com -c user.name="Pranay Harjai"`); `--no-ff` merges + pushes
  **only on explicit authorization**; each stage ends green (unit tests + on-device smoke on `74ed1bb5`).

## Roadmap (7 phases, dependency order)

| Phase | Name | Depends on | Outcome | Status |
|------:|------|-----------|---------|--------|
| 1 | Activity truth + intraday foundation + time-of-day charts | — | Correct active minutes / total burn; steps/calories/HR "through the day" graphs | **In progress** |
| 2 | Sleep staging + score + hypnogram | P1 | Full sleep card (score, stages, hypnogram) | Planned |
| 3 | Cardiovascular + HR zones + cardio load + strain + ACWR | P1, profile | Strain card: zone-time bars, daily strain, ACWR gauge | Planned |
| 4 | Recovery + stress (extend ReadinessScoreCalculator) | P2, P3, P1 | Recovery ring + driver breakdown; daily stress curve | Planned |
| 5 | Body composition intelligence | P1, nutrition | Body-comp trends + energy-balance narrative | Planned |
| 6 | Unified daily/weekly/MONTHLY insights + goal-aware projections | all prior | Insights screen with day/week/month toggle + projection cards | Planned |
| 7 | Polish, performance, release | all | Fast, battery-respectful, accessible release | Planned |

> **Phase 8 — Wearable companion (Wear OS), FUTURE / not scheduled.** A first-party watch app/service
> (Health Services + Wearable Data Layer) that reads sensors directly and removes the "what Samsung
> exports" ceiling — turning every degraded estimate (active calories, strain, recovery, stress, sleep
> staging) into measured data. We design Phases 1–7 to leave seams for it (see
> [phase-8-wearable-future.md](phase-8-wearable-future.md)); we plan its own features later.

Phase detail files:
- [phase-1-activity-intraday.md](phase-1-activity-intraday.md) — **fully detailed**
- [phase-2-sleep.md](phase-2-sleep.md)
- [phase-3-cardio-load-strain.md](phase-3-cardio-load-strain.md)
- [phase-4-recovery-stress.md](phase-4-recovery-stress.md)
- [phase-5-body-composition.md](phase-5-body-composition.md)
- [phase-6-insights-projections.md](phase-6-insights-projections.md)
- [phase-7-polish-release.md](phase-7-polish-release.md)
- [phase-8-wearable-future.md](phase-8-wearable-future.md) — **future / not scheduled**
## Confirmed codebase facts (reuse; do not reinvent)

- **Activity read path** `HealthConnectManager.fetchHealthSummary(goals, date, bmrFallback)` aggregates
  over a correct local-day window. Three bugs (all fixed in Phase 1):
  - **Active time** fabricated at `HealthConnectManager.kt:238-240` (dup `:307-309`):
    `sessions.sum(durationMinutes).coerceAtLeast((steps/100).coerceAtMost(60))`. The `coerceAtMost(60)`
    caps non-exercise active minutes → Samsung's 100 shows as 60.
  - **Total burn** `aggregateTotalCalories(...) ?: activeCalories` (`:206`) — lossy fallback to
    active-only. The app already computes `caloriesOut = activeCalories + basalMetabolicRate`
    (`ActivityRepositoryImpl.kt:170,187`).
  - **Active calories** is an honest HC aggregate (`:202`); if wrong on-screen it's cache staleness.
- **Persistence**: daily `snapshot` table stores `HealthSummary` as a JSON blob; today's row is `PARTIAL`
  and today-guarded against remote overwrite (`SnapshotSyncer.kt:126-131,257-258`). `vitals_samples` has
  `kind='rollup'` (used) + `kind='sample'` (dead scaffolding). `deepSleepMinutes` fully plumbed but always
  written null. Room schema **v15** (`AppDatabase.kt`). Sync: `SyncScheduler` (WorkManager ~15 min +
  expedited after writes) → `SyncWorker` → `SyncOrchestrator` → per-table syncers.
- **Logout is data-safe at the auth layer**: `AuthRepositoryImpl.signOut()` (`:66-74`) only clears the
  Supabase session — it does not clear Room. Data is `userId`-keyed; the risk is re-attachment on re-login.
- **Sleep today**: only `SleepSessionRecord` boundaries read (`fetchSleepSummary` `:699-724`) → duration
  only. No `.stages` parse, no score; `SleepSection` (`HealthScreen.kt:266`) shows duration only.
- **Reusable foundations**: `ReadinessScoreCalculator` (HRV .40 + RHR .30 + sleep .30, 28-day baseline,
  dynamic weight-redistribution); `BodyEnergy` (Mifflin-St-Jeor BMR, TDEE, BMI, fat/lean — single source
  of truth); `BuildCrossInsightsUseCase` (pure LocalDate alignment, 7-day window);
  `BuildWeeklyInsightsUseCase` (single-week text cards); charts `drawTrend`/`TrendLine`/`Sparkline`
  (index-x only), `ChartPalette`/`chartSeries1-3()`, `TrendHybridLayout`, `InsightsCrossCharts`
  (`DualTrend`/`Scatter`). No time-of-day axis, no bar chart, no hypnogram, no intraday storage yet.
