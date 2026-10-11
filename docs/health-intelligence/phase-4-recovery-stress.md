# Phase 4 — Recovery + stress (extend ReadinessScoreCalculator)

> Status: **planned** (stub — expand to full stage-by-stage detail when reached).

**Goal:** Whoop-style recovery and all-day stress. Extend `ReadinessScoreCalculator` from its current
HRV(.40) + RHR(.30) + sleep(.30) blend to HRV(~.40) + RHR(~.25) + respiratory(~.10) +
sleep-performance(~.15) + recent-strain(~.10) with 28-day rolling baselines and red/yellow/green bands,
keeping its dynamic weight-redistribution for missing inputs. Add a 0–100 **stress** score (HRV
suppression + HR-above-rest across the day) off the Phase-1 intraday HR series + intraday HRV where
available.

**Depends on:** Phase 2 sleep-performance score, Phase 3 strain, Phase 1 intraday HR.

**Outcome:** a recovery ring with band + driver breakdown, and a daily stress curve/summary — both consumed
by the existing readiness surfaces (`VitalsRepositoryImpl`, `BuildCrossInsightsUseCase`).

## Methodology
- Recovery 0–100 = weighted deviation of this morning's physiology from a 28-day baseline; bands
  red (<34) / yellow (34–66) / green (>66) in Whoop's convention (final band thresholds TBD in detail).
- Stress 0–100 = lower HRV + higher HR-above-rest ⇒ higher stress, sampled across the day.
- Extend the existing calculator rather than replace it; preserve `missingDataFlags` + establishing-
  baseline behavior. Pure, unit-tested.

## Known entry points
- `domain/vitals/ReadinessScoreCalculator.kt` (extend inputs + weights).
- Consumers: `VitalsRepositoryImpl` (dashboard/date-aware), `BuildCrossInsightsUseCase` (per-day trend).
- Phase-1 intraday HR/HRV for stress; Phase-2 sleep score + Phase-3 strain as new recovery inputs.
