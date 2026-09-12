# 02 — TrackMe (Kotlin) vs TrackMe‑flutter — Feature Diff

**Bottom line:** TrackMe‑flutter is an honest, near‑1:1 port of Kotlin TrackMe. The
headline engines (`ProgressAnalyticsEngine`, `ReadinessScoreCalculator`, muscle map,
live‑session state machine with process‑death resume, offline‑first LWW sync, the
873‑exercise catalog, auth/onboarding/planner/PRs/share) exist in **both**, with
matching formulas and constants (Epley 1RM, log‑growth cap 200, fatigue tau 36/60/72h,
trimmed‑regression 30/90/365d, Mifflin‑St Jeor BMR hardcoded age‑25/male, base‑80
readiness). Neither actually implements the README's "7‑day rolling window" — both
average full history.

**Porting decision for HelloHealth (Kotlin/Compose):** use **Kotlin TrackMe as the
code source of truth** for essentially every gym feature (same language → drop‑in
Compose/Room/Hilt, and it's the deeper implementation incl. Wear + notification), but
**reproduce a few Flutter design wins**. **Skip the Wear companion** unless HelloHealth
targets a watch.

---

## Feature‑by‑feature

| Feature | Kotlin | Flutter | Port from |
|---|---|---|---|
| **Live session engine** (state machine, quick‑log, skip/prev, pause, edit/delete, finish→duration, process‑death rest‑timer resume) | ✅ full — `WorkoutSessionManager` `@Singleton`; rest‑end persisted to DataStore, restored across process death | ✅ full — Riverpod `keepAlive` mirror; adds **prefill‑from‑last‑set** | **Kotlin**; fold in Flutter's prefill touch |
| **Live‑session foreground notification** with in‑notification set logging (±weight/reps, save, skip/adjust rest, exercise nav) | ✅ full — service + renderer + action receiver subsystem | 🟡 partial — lighter `flutter_foreground_task` mirror | **Kotlin** (richer, native‑idiomatic) |
| **Exercise catalog** (873 free‑exercise‑db, search + detail) | ✅ full — seeded to Room; static images + YouTube launch | ✅ **full+** — animated **GIF** detail (CachedNetworkImage) + YouTube | **Kotlin** for seeding/search; reproduce Flutter's **animated‑GIF** detail via **Coil‑gif** |
| **Planner / routines** | 🟡 weekly‑only (no plan_type) | ✅ full — **weekly/monthly/custom** `planType` enum + auto slot‑keys (D01–D31, C01–C99) | **Kotlin** mechanics + **adopt Flutter's plan‑type design** (clearest Flutter win) |
| **Adaptive target‑param sheet** (fields vary by logging type) | ✅ full — `TargetParamSheet` | ✅ full — driven by explicit `LoggingType` derivation | **Kotlin**; mirror Flutter's explicit `LoggingType` derivation |
| **Body‑analytics engine** (11 modules) | ✅ full — `ProgressAnalyticsEngine` `@Singleton`, pure, 4 Room caches | ✅ full — same formulas in a `compute()` isolate (explicit "direct port") | **Kotlin** (the original) |
| **Readiness score** (0–100 HRV/RHR/sleep vs baseline, weight redistribution, <7‑day gate) | ✅ full — base‑80 weighted `.40/.30/.30` | ✅ full — same weights/clamps | **Kotlin**; fix the "7‑day rolling" README claim once |
| **Rotatable front/back muscle map** (colored by growth index) | ✅ full — inline `cos()`‑driven Canvas in `ProgressScreen` | ✅ full — extracted standalone `CustomPainter` with **point‑in‑polygon tap** | **Kotlin** (already Compose Canvas); borrow Flutter's **extract‑to‑tappable‑widget** design |
| **Progress screen UI** (readiness gauge, heatmap, physique scrubber, forecasts) | ✅ full — animated gauge, flippable heatmap, mesh gradient | ✅ full — **3‑tab** (Overview/Muscles/Forecasts) + glassmorphism | **Kotlin**; consider Flutter's 3‑tab organization |
| **Health Connect integration** | ✅ full — `HealthMetricMapper` 40+ types | 🟡 partial — cross‑platform `health` plugin, focused subset; iOS uses SDNN as RMSSD proxy | **Kotlin** (broader); HelloHealth's `HealthConnectManager` is even stronger |
| **Cloud sync** (offline‑first LWW ↔ Supabase, tombstones, FK‑ordered) | ✅ full — `SyncManager` LWW on `updatedAt`, 9 tables FK‑ordered; **health tables NOT synced (local‑only)** | ✅ full — generic LWW in a background isolate; **same health‑not‑synced limitation** | **Kotlin**; fix health‑not‑synced on the way in |
| **Auth & onboarding** (Google via Supabase, unit prefs, goal) | ✅ full — tokens in EncryptedSharedPreferences | ✅ full — 3‑step onboarding | **Kotlin** (HelloHealth already uses gotrue + Credential Manager) |
| **Personal records** | 🟡 max‑weight only | 🟡 max weight **AND max reps** | **Kotlin** + adopt Flutter's max‑reps; ideally add Epley est‑1RM PR |
| **Home / Today** (streak, train‑today, readiness, date picker) | ✅ full — streak, context greeting | ✅ **full+** — **readiness‑aware** greeting ("Recover well,") | **Kotlin**; reproduce Flutter's readiness‑aware wording |
| **Routine share‑as‑text** | ✅ full — use case + share intent | ✅ full — `share_plus` + emoji | **Kotlin** |
| **Wear OS companion + phone↔watch bridge** (on‑watch workout, HR zones, idempotent command replay, LWW dedupe, MET calorie fallback) | ✅ full — `:wear` + `:wear-bridge` `WearProtocol` | 🔴 **absent** | **Kotlin (only option)** — **DEFER**; port only if HelloHealth targets Wear |

---

## Kotlin‑only features
- Wear OS companion app (`:wear`) — on‑watch active‑workout UI (sets, rest timer, HR zones, muscle map, readiness, summary) via Compose for Wear + Health Services.
- Phone↔watch bridge (`:wear-bridge` `WearProtocol`) — `SessionStatePayload`/`DayPayload` publish + command payloads over DataClient/MessageClient, idempotent `commandId` dedupe (last 300), stale‑session rejection, offline replay queues, LWW.
- Watch‑side health collection (`WearHealthMetricCollector`) with MET‑based calorie fallback.
- Action‑rich foreground‑notification set‑logging subsystem (renderer + state mapper + action receiver).
- Standalone (currently under‑used) unit‑tested engines: `OneRMProjectionEngine`, `PlateauDetector`, `MuscleFatigueCalculator`, `ConsistencyMatrixGenerator`.

## Flutter‑only features (reproduce as design, not code)
- **Plan types** weekly/monthly/custom + auto slot‑key generation.
- **Readiness‑aware** home greeting wording.
- **Animated exercise‑GIF** catalog detail.
- PRs track **max reps** in addition to weight.
- Glassmorphism / 3‑tab Overview‑Muscles‑Forecasts progress layout.
- Muscle map as a **standalone tappable** widget (point‑in‑polygon hit‑testing).

## Shared defects to fix on the way into HelloHealth
- BMR ignores age/sex (hardcoded 25/male) → read `UserProfile`.
- PRs are weight‑only (no Epley estimated‑1RM rep PR).
- Health snapshots are local‑only (not cloud‑synced) in both.
- Dead/duplicated standalone engines in Kotlin (engine re‑implements inline; `EngineAnalyticsResult.oneRmProjections` faked as `current1RM*1.02`).
