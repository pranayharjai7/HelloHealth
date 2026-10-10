# Phase 5 — Body composition intelligence

> Status: **planned** (stub — expand to full stage-by-stage detail when reached). Small phase; reorderable
> before Phase 4 if the user's body data is richer than their vitals data.

**Goal:** deepen body metrics into a composed story. Build on `BodyEnergy` (Mifflin-St-Jeor BMR, TDEE,
BMI, fat/lean mass — the single source of truth) and `BodyMetric`/`BodyAnalytics`, adding trend
decomposition (weight EWMA vs daily noise), fat/lean trajectory, and **energy-balance reconciliation**
against the Phase-1-corrected total burn and logged nutrition.

**Depends on:** Phase 1 correct total burn; nutrition already present.

**Outcome:** a body-composition trends screen with fat/lean trajectories and an honest energy-balance
narrative ("over the last N days you averaged a ~X kcal/day deficit/surplus → ~Y kg/week trend"),
clearly-labelled estimate, never medical advice.

## Known entry points
- `domain/health/BodyEnergy.kt` (BMR/TDEE/BMI/fat/lean; calorieBudget 7700 kcal/kg; suggestedGoals).
- `domain/usecase/BodyAnalyticsUseCase.kt` + `domain/model/BodyAnalytics.kt`/`BodyMetric.kt`.
- Phase-1 corrected `totalCalories` + nutrition `caloriesIn` for the balance reconciliation.
- Reuse `TrendLine`/`TrendHybridLayout`; projection logic shared with Phase 6.
