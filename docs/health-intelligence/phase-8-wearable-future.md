# Phase 8 (future) — Wearable companion (Wear OS) + sensor-direct data

> Status: **future / not scheduled.** This doc exists so we design Phases 1–7 to accommodate a wearable
> gracefully. We will plan the wearable's own features in full later. Do NOT build any of this yet —
> only leave the seams (noted under "Code seams to leave open").

## Why a wearable changes everything

Today HelloHealth is limited to what the phone's writer apps (e.g. Samsung Health) choose to export to
Health Connect. Observed on a Galaxy Watch4 Classic → Samsung Health → Health Connect: we get total
calories, steps, distance, a daily HR average, and summary sleep — but **no `ActiveCaloriesBurnedRecord`,
no continuous intraday heart-rate, no beat-to-beat HRV, no raw sleep staging**. Every "degrade
gracefully / estimate" caveat in the current plan exists because of that ceiling.

A first-party wearable app/service (Wear OS 3+, e.g. Galaxy Watch4 Classic) reads the sensors (or Wear OS
**Health Services**) directly and syncs to the phone via the **Wearable Data Layer API**, removing the
ceiling.

## What a wearable unlocks (map to phases)

| Capability | Source on watch | Unlocks / upgrades |
|---|---|---|
| Continuous intraday **heart rate** | Health Services `PassiveMonitoringClient` / `ExerciseClient` | Real active calories (Keytel), accurate **cardio load / strain** (Phase 3), stress (Phase 4) — no longer degraded |
| Continuous **HRV** (RMSSD/SDNN), incl. overnight | Health Services / Samsung Health Sensor SDK | True **recovery/readiness** (Phase 4) from the overnight HRV curve, not one daily sample |
| First-party **sleep staging** (accel + HR + HRV) | On-watch sensors | Accurate deep/REM/light/awake + hypnogram + sleep score (Phase 2) without relying on Samsung's export |
| **SpO₂, respiratory rate, skin temperature** overnight | BioActive sensor | Apnea signals, illness early-warning, recovery context |
| **ECG + body composition (BIA)** | Samsung BioActive (needs Health Sensor SDK + privileged partner approval for raw ECG; BIA more accessible) | Real body composition without a smart scale (Phase 5); cardiovascular screening |
| Elevation, cadence, pace, auto-exercise detection | Health Services | Grade-aware MET active burn; per-session Banister TRIMP (Phase 3) |
| Glanceable **tiles / complications**, on-wrist workout, haptic nudges | Wear OS Tiles/ongoing-activity | Whoop/Garmin UX parity |

## Architecture when we build it
- New **Wear OS module** (`:wear`) targeting Wear OS 3+, its own build/release.
- **Health Services** for battery-efficient all-day passive metrics + high-frequency exercise metrics.
- **Wearable Data Layer API** (DataClient/MessageClient) to sync watch → phone; phone ingests into the
  same `intraday_series` table (Phase 1) and the daily rollups, so downstream calculators are unchanged.
- Optional: write watch-derived records back into Health Connect so the rest of the ecosystem benefits.
- Samsung **Health Sensor SDK** only where Health Services is insufficient (raw ECG/PPG, BIA).

## Code seams to leave open (do this DURING Phases 1–7)

The intent: a wearable becomes an additional **data source** behind the same interfaces, not a rewrite.

1. **Calorie + metric estimators are tiered and source-aware.** The active-calories calculator
   (`domain/health/ActiveEnergyEstimator`, Phase 1) already defines a `Tier A — HR-based (Keytel)` path
   that is stubbed/unused today with a `// WEARABLE:` comment. When watch HR arrives, that tier lights up
   with no caller change. Keep every derived-score calculator (strain, recovery, stress, sleep) taking
   its physiological inputs as parameters (never reading a source directly), so a richer source is a
   better input, not a new branch.
2. **`intraday_series` is the single intraday substrate.** Watch HR/HRV/SpO₂ samples land in the same
   table (new `metric` values: `hrv`, `spo2`, `respiratory`, …). No schema reshape needed — the table was
   designed metric-typed for exactly this. Leave a `// WEARABLE: additional metrics land here` note on the
   `metric` constants in `IntradaySeriesEntity`.
3. **Data-source provenance.** Where a reading's origin matters (watch vs phone vs reconstruction), carry
   a lightweight source/quality flag (the `isEstimate`/`quality` pattern from the approved decisions) so
   the UI can show "measured" vs "estimated," and so watch data can supersede reconstructed values.
4. **Sync orchestration is pluggable.** New syncers register via `@IntoSet` in `SyncModule`; a future
   watch-ingest path is another registered component, not a change to the orchestrator.
5. **Don't hard-code "Health Connect" as the only reader.** Keep `HealthConnectManager` behind the
   repository boundary (already true) so a `WearDataSource` can sit beside it feeding the same repository.

Leaving these seams costs ~nothing now and avoids a rewrite later. We will write the full wearable plan
(features, module setup, battery budget, partner-SDK approvals, watch UX) as its own effort when
instructed.
