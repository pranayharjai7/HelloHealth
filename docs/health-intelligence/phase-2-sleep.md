# Phase 2 — Sleep staging + score + hypnogram

> Status: **planned** (stub — expand to full stage-by-stage detail when Phase 1 completes).

**Goal:** full sleep intelligence. Parse `SleepSessionRecord.stages` (awake/REM/light/deep) that today's
`fetchSleepSummary` (`HealthConnectManager.kt:699-724`) ignores; compute per-stage minutes + sleep
efficiency + onset latency + WASO; build a weighted 0–100 **sleep score** with per-component bands
(needs-attention / fair / good / excellent) from AASM/NSF reference ranges; render a **hypnogram**
(stage-band renderer over the Phase-1 TimeOfDayChart time axis). Finally plumb the already-scaffolded
`deepSleepMinutes` (entity + migration + syncer exist, always written null) plus new stage fields
(REM/light/awake) through the four serialization points and the vitals rollup so they feed readiness.

**Depends on:** Phase 1's time-axis chart primitive and intraday storage pattern.

**Outcome:** `SleepSection` (currently duration-only at `HealthScreen.kt:266`) becomes a full sleep card
with score, stage breakdown, latency/efficiency, and a hypnogram; deep sleep stops being null and starts
feeding `ReadinessScoreCalculator`.

## Methodology (reference ranges)
- Total sleep time target 7–9 h.
- Stage proportions: REM ~20–25%, deep (N3) ~13–23%, light (N1+N2) ~50–60%.
- Sleep efficiency (asleep ÷ in-bed) > 85% good; onset latency < 20 min ideal; low WASO / awake.
- Sleep score = weighted blend of duration, deep%, REM%, efficiency, latency, restfulness; each component
  maps to a band. Pure, unit-tested calculator (mirror `ReadinessScoreCalculator`'s shape + degradation).

## Known entry points
- `HealthConnectManager.fetchSleepSummary` — extend to read `SleepSessionRecord.stages`.
- `deepSleepMinutes` plumbing: `VitalsSampleEntity.kt:44`, `Migrations.kt:333`, `VitalsSampleSyncer`,
  `VitalsRepository` — already present, always null today; add REM/light/awake alongside.
- The four serialization points (see README working rules).
- Phase-1 `TimeOfDayChart` for the hypnogram axis; a new stage-band renderer is net-new.
