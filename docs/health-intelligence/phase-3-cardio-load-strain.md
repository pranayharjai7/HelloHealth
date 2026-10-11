# Phase 3 — Cardiovascular + HR zones + cardio load + strain + ACWR

> Status: **planned** (stub — expand to full stage-by-stage detail when reached).

**Goal:** training-load intelligence. From the Phase-1 intraday HR series, compute Karvonen HRR zones 1–5
(HRmax 211 − 0.64·age, user-overridable; zone lower bound = HRrest + pct·HRR), **Edwards summated-HR-zone
TRIMP** (Σ minutes-in-zone × zone), per-session **Banister TRIMP** (sex-specific), a 0–21 **strain** score
(log-rescaled daily TRIMP against a personalized baseline), and **ACWR** (7-day EWMA ÷ 28-day EWMA,
sweet-spot 0.8–1.3, > 1.5 overreach flag). Persist daily load/strain in the snapshot + a load-history
rollup; also persist VO2max/BP/glucose into the vitals rollup (today read into `HealthSummary` but never
persisted, so they don't feed trends).

**Depends on:** Phase 1 intraday HR + storage; profile age / sex / resting HR.

**Outcome:** a cardiovascular/strain card — zone-time bars (Phase-1 BarChart), daily strain, and an ACWR
load-balance gauge with overreach warnings.

## Methodology
- Zones via Karvonen HRR; HRmax = 211 − 0.64·age (Nes), overridable in profile.
- Edwards TRIMP = Σ (minutes in zone *i* × *i*), i = 1..5.
- Banister TRIMP = duration · ΔHRr · 0.64·e^(1.92·ΔHRr) [men] / 0.86·e^(1.67·ΔHRr) [women],
  ΔHRr = (HRavg − HRrest)/(HRmax − HRrest).
- Strain 0–21 = log rescale of day TRIMP vs personal baseline.
- ACWR = 7d EWMA ÷ 28d EWMA; 0.8–1.3 sweet spot, > 1.5 overreach.
- All pure, unit-tested calculators; degrade gracefully when intraday HR is absent (estimate flag).

## Known entry points
- Phase-1 `intraday_series` HR buckets; new daily load fields through the four serialization points.
- Profile for age/sex/RHR (and HRmax override).
