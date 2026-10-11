# Phase 6 — Unified daily/weekly/MONTHLY insights + goal-aware projections

> Status: **planned** (stub — expand to full stage-by-stage detail when reached).

**Goal:** compose everything into insights and projections. Extend `BuildCrossInsightsUseCase` (currently a
fixed 7-day alignment) and `BuildWeeklyInsightsUseCase` (single-week, text-only, no monthly, no
projections) into a shared alignment + momentum engine that produces **daily, weekly, and monthly**
rollups across all dimensions (activity, sleep, cardio load, recovery, stress, body comp), plus
**goal-aware projections**: linear/EWMA extrapolation per daily series with a confidence band and
"to hit X by Y, do Z" guidance. Extend `ActivityGoals` (currently steps/activeCalories/activeMinutes only
— no distance or total-cal goal) as projection targets require.

**Depends on:** all prior phases' persisted daily series.

**Outcome:** a redesigned Insights screen with day/week/month toggles and projection cards; new monthly
charts reuse the Phase-1/2/3 primitives. Everything stays descriptive, never diagnostic/medical
(preserving the existing `InsightsCrossCharts` copy discipline + "not medical advice" note).

## Methodology
- Monthly aggregation = same LocalDate-alignment pattern as `BuildCrossInsightsUseCase`, widened window +
  monthly bucketing.
- Projections = linear regression and/or EWMA extrapolation per series, 2–4 weeks out, with a confidence
  band; goal-aware inversion ("required weekly pattern to reach goal by date"). Pure, unit-tested;
  clearly-labelled estimates.

## Known entry points
- `domain/usecase/BuildCrossInsightsUseCase.kt` + `BuildWeeklyInsightsUseCase.kt` (extend to one engine).
- `domain/model/CrossDimensionInsights.kt` (widen; add projection models).
- `domain/model/ActivityGoals.kt` + `GoalsEntity`/`GoalsRepositoryImpl` (new goal targets → migration).
- `ui/insights/InsightsScreen.kt` + `InsightsCrossCharts.kt` (day/week/month toggle, projection cards).
