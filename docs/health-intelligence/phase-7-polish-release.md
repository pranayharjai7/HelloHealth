# Phase 7 — Polish, performance, release

> Status: **planned** (stub — expand to full stage-by-stage detail when reached).

**Goal:** production hardening of everything the program added.

- **Battery / performance audit of intraday reads** — only fetch intraday for *today* (and on-demand for a
  selected past day), not every day every sync cycle; cache the last-fetched window; verify WorkManager
  cadence; measure the heavier `aggregateGroupByDuration` + full-day `HeartRateRecord` reads.
- **Backfill strategy** — intraday is forward-only (HC history reads are heavy); decide which daily
  rollups/scores to backfill from HC history via a one-time count-gated backfill like `VitalsBackfiller`.
- **Empty / estimate-state polish** across all new cards (every degraded score shows its labelled estimate
  + reason).
- **Accessibility + dark-mode sweep** on every new chart (BarChart, TimeOfDayChart, hypnogram, zone bars,
  recovery ring, stress curve, projection cards).
- **Supabase index / RLS review** for any new synced rollup columns (intraday stays device-local).
- **Final dataviz validation** sweep (`validate_palette.js`, one-axis, legend + direct labels, CVD-safe).
- **Signed release** (deferred — `.jks` missing on the machine).

**Depends on:** all prior phases.

**Outcome:** a fast, consistent, battery-respectful release build.
