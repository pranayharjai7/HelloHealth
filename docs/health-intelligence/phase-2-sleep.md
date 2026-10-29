# Phase 2 — Sleep staging + sleep score + hypnogram

> Status: **IN PROGRESS** (branch `feature/sleep-intelligence`, off master after Phase 1 merge `e1ec128`).

**Goal:** full sleep intelligence — parse `SleepSessionRecord.stages` (today ignored), compute per-stage
minutes + efficiency + latency + WASO, a transparent 0–100 **sleep score** with per-component bands
(needs-attention / fair / good / excellent), and a **hypnogram** (stage-band chart over a time axis).
Finally populate the always-null `deepSleepMinutes` + new stage fields and feed them to readiness.

**Confirmed facts (from the codebase map):**
- `SleepSessionRecord.stages` **IS available** (connect-client 1.1.0-alpha12). Stage constants:
  `STAGE_TYPE_UNKNOWN=0, AWAKE=1, SLEEPING=2, OUT_OF_BED=3, LIGHT=4, DEEP=5, REM=6, AWAKE_IN_BED=7`.
  Stages are guaranteed sorted/non-overlapping/within-session. No per-stage aggregate — sum manually.
  **No new HC permission** (SleepSessionRecord read already granted).
- `fetchSleepSummary` (`HealthConnectManager.kt:804`) already holds the full records; it only derives
  boundary minutes. Returns `Triple<Long,Instant?,Instant?>` → must widen to a richer struct.
- Room schema is **v16**; `deepSleepMinutes`/`sleepDurationMinutes` columns already exist (MIGRATION_9_10).
- `persistVitalsRollup` (`ActivityRepositoryImpl.kt:230`) writes `deepSleepMinutes = null` (line 242) —
  the one spot to feed real data once `HealthSummary` carries it.
- Readiness uses sleep *duration* only (0.30 weight); `deepSleepMinutes` on `HealthMetricsData` is never
  read — a seam to upgrade to the composite sleep score.
- Hypnogram: reuse `timeOfDayFraction` + `ChartScaffold` + `InteractiveChart`/`ChartValueTable`; BUILD a
  new stage-band renderer + `SleepStageSegment` input + stage→lane/color map (the y-axis is a
  categorical stage ladder, not a numeric niceAxis).

## Methodology — the sleep score (documented, reproducible)

Health Connect gives stages; the SCORE is ours. Transparent weighted blend (Fitbit-class breakdown +
AASM / National Sleep Foundation reference ranges), each component → 0..1 then → band:

| Component | Weight | Target (full credit) | Degrades toward 0 |
|---|---|---|---|
| Duration (TST) | 0.40 | 7–9 h | <6 h or >10 h |
| Deep % of TST | 0.15 | 13–23% | <10% |
| REM % of TST | 0.15 | 20–25% | <15% |
| Efficiency (asleep÷in-bed) | 0.15 | ≥90% (good ≥85) | <75% |
| Restfulness (WASO/awakenings) | 0.10 | low WASO | high WASO |
| Onset latency | 0.05 | <20 min | >45 min |

Score = Σ(weight × component). Bands: ≥85 excellent · 70–84 good · 50–69 fair · <50 needs attention.
Degrade gracefully: if stages are absent (duration-only session), score from duration alone + flag
`isEstimate`; per-component bands shown only for components we have data for. Pure, unit-tested
(mirrors `ActiveMinutesEstimator`/`ReadinessScoreCalculator` shape).

Stage derivation from `.stages` (pure): deep/rem/light minutes = Σ segment durations by type; awake =
AWAKE+AWAKE_IN_BED within the sleep window; asleep = deep+rem+light(+SLEEPING); in-bed = session span;
latency = first non-awake stage start − session start; WASO = awake minutes between first and last
asleep stage; efficiency = asleep ÷ in-bed.

## Stages

**S0 — baseline.** Branch off master (done). Confirm suite green, v16.

**S1 — pure stage + score domain (no I/O, fully tested first).** New `domain/health/SleepStages.kt`
(`SleepStageSegment(startMs,endMs,stage)`, `SleepStageSummary(deep/rem/light/awake min, asleep, inBed,
latency, efficiency, waso, isEstimate)`, pure `summarizeStages(sessionStart, sessionEnd, segments)`) +
`domain/health/SleepScore.kt` (`SleepScore(score, band, components)`, pure `computeSleepScore(summary)`).
Unit tests: reference-range cases, missing-stages degrade, band thresholds, awake/latency edge cases.

**S2 — read stages from Health Connect.** Widen `fetchSleepSummary` → a `SleepRead` struct carrying the
existing boundary data + `List<SleepStageSegment>`. Map `SleepSessionRecord.stages` across the day's
overlapping sessions. Degrade: no stages → empty segment list (duration-only). Pure segment-flatten
helper unit-tested; the HC read verified on-device (does Samsung write stages?).

**S3 — carry through HealthSummary + persist (serialization points).** Add stage/score fields to
`HealthSummary`; set from `SleepRead` + `summarizeStages`/`computeSleepScore` in `fetchHealthSummary`.
Thread the new vitals-rollup fields (deep/rem/light/awake min, score) through: `VitalsSampleEntity` +
`MIGRATION_16_17` (ALTER TABLE ADD COLUMN; v17 + 17.json + MigrationTest) + `VitalsRepository.upsertRollup`
+ impl build/mappers + `HealthMetricsData` + `VitalsSampleSyncer` DTO/toDto/toEntity + Supabase columns.
Replace the `deepSleepMinutes = null` at `persistVitalsRollup:242`. Round-trip + non-null-when-data tests.

**S4 — readiness upgrade (optional-safe).** Feed the sleep score (or deep/rem) into
`ReadinessScoreCalculator`'s 0.30 sleep slot, replacing the duration-only penalty; keep graceful
degradation + existing tests green (adjust expectations).

**S5 — hypnogram renderer.** New `ui/charts/Hypnogram.kt`: stage-band chart (Awake/REM/Light/Deep lanes)
over the time axis, reusing `timeOfDayFraction` + `ChartScaffold` (categorical y) + `InteractiveChart`
(`ChartProjection` anchored per segment, tooltip "01:40–02:15 · Deep") + `ChartValueTable`. Stage→lane
+ color map. Pure `timeOfDayFraction` already tested; add a lane-mapping test.

**S6 — Sleep card UI.** Replace `SleepSection` (duration-only) with a full card: score + band, stage
breakdown (deep/rem/light/awake with per-component rating), latency/efficiency, the hypnogram. Honest
empty/estimate states. Wire the new `HealthSummary` sleep fields through `HealthUiState`.

**S7 — close-out.** Full suite + migration test + R8; before/after screenshots; dataviz check on the
hypnogram; no new deps.

## Verification
- Pure calculators unit-tested FIRST (S1). Round-trip + migration tests for the new fields (S3).
- On-device: does the device write sleep stages to HC? (settle in S2). Sleep card shows score + stages +
  hypnogram; tap a stage band → "01:40–02:15 · Deep"; degrade cleanly on a duration-only day.
- Cross-check score shape against Samsung's (today 57, 4h24m, deep 25m, REM 38m, awake 14m, latency 9m).

## Risks
- **Does Samsung write stages to HC?** Settle on-device in S2 before building the hypnogram UI.
- Supabase column add (external) — same pattern as prior synced fields.
- Score calibration — document weights as named constants; cross-check vs Samsung's number.
