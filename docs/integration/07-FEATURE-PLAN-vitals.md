# 07 — Feature Plan: Vitals & Recovery card (HealthSync intent)  · Phase P3

Broaden Health Connect ingestion to HealthSync's documented vitals coverage and compute
a unified **Readiness/Recovery** score. HealthSync has **no code** — this implements its
*intent*. **Depends on P0.** Read‑only Android/Health Connect (no HealthKit/iOS).

## READ
- HealthSync: `DESIGN.md` **section 2** (the Health Connect type‑mapping table = the schema spec) + `CONSTRAINTS.md`.
- HelloHealth: `data/health/HealthConnectManager.kt` (nullable‑guarded reads), `data/repository/ActivityRepositoryImpl.kt` (read‑through cache), `domain/model/HealthSummary.kt` (already has SpO2/BP/glucose/VO2max/sleep).
- TrackMe: `domain/analytics/recovery/ReadinessScoreCalculator.kt`.

## LEARN
- Full HC vitals type set + **exact conversions**: SpO2 0–100 → fraction; blood glucose mmol/L × 18.0182; BMR rate → energy integration; **RMSSD ≠ SDNN** (tag the HRV variant); resting HR; respiratory rate; body temperature; hydration.
- Which types Samsung may **not** export (must null‑guard) and which are **permanently lost** (stress, energy score, sleep apnea/irregular‑rhythm, skin temp, sleep score) — document as unsupported non‑goals.
- Readiness weighting (base‑80, .40 HRV / .30 RHR / .30 sleep) + **dynamic weight redistribution** when inputs are missing + the "Establishing Baseline" state (<7 days of history).
- The **canonical‑record** rule: content‑hash id excluding wall‑clock; UTC + tz‑offset (for idempotent re‑sync).

## EXECUTE
1. Extend `HealthConnectManager` reads to the full vitals type set, each with its conversion rule and a **per‑type null‑guard**.
2. Extend `HealthSummary` with the missing fields (respiratoryRate, bodyTemperature, restingHeartRate, hydration).
3. Add a `vitals_samples` Room table (HealthSync canonical record: content‑hash id, type tag, value, metadata JSON incl. HRV variant) + sync columns + Supabase mirror; register in `SyncManager` (push‑mostly).
4. Port `ReadinessScoreCalculator` fed from Health Connect (HRV RMSSD, resting HR, sleep duration/stages).
5. Add the **Vitals & Recovery** card (RHR, HRV, SpO2, BP, glucose, respiratory rate, sleep, VO2max + readiness score) + a vitals trends screen + nav route, reusing Canvas chart idioms.
6. **Publish the readiness score to the shared rollup** so the Workout card can recommend intensity and the Wellness score can consume it.

## TEST
- Unit: each conversion (SpO2, glucose, BMR integration, HRV tagging); readiness with **missing inputs** (redistribution) and **<7‑day** baseline.
- On‑device: which types actually return data on the target device (Samsung gaps).
- Graceful‑degrade: HC absent → card shows a defined placeholder (reuse the nullable‑client pattern), no crash.

## REVISE / non‑goals
- Android‑only **read** — no HealthKit write, no iOS, no HealthSync relay/encryption pipeline (those stay out of scope).
- Fix readiness to use a **defined recent window** consistently (both TrackMe variants incorrectly average full history despite the "7‑day" claim).
- Permanently‑lost Samsung metrics are documented, not faked.
