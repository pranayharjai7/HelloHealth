# 04 — Feature Plan: Workout card (from TrackMe)  · Phases P4 (+P5 analytics)

Bring TrackMe's gym tracking into HelloHealth as the **Workout / Training** card.
Kotlin TrackMe is the code source of truth; reproduce a few Flutter design wins
(see [`02`](02-TRACKME-KOTLIN-VS-FLUTTER-DIFF.md)). **Depends on P0** (offline‑first spine).
Wear OS is explicitly **out of scope**.

## READ
- TrackMe: `app-info.md`; `domain/.../WorkoutSessionManager.kt`; the Start/Log/Finish/Skip use cases; `data/local` `AppDatabase` + migrations + the 6 workout tables + 4 analytics caches; `assets/exercises.json`; `sync/SyncManager.kt`; `ui/workout/**` (session, planner, exercise search/detail), `ui/progress/ProgressScreen.kt`; `domain/analytics/ProgressAnalyticsEngine.kt` + models (P5).
- TrackMe‑flutter (design only): `features/planner/**` (plan types), `features/session/**` (prefill), `widgets/muscle_map/**`, `features/home/**` (readiness greeting), exercise‑GIF detail.
- HelloHealth: `di/*Module.kt`, `data/repository/*`, `data/local/AppDatabase.kt` (post‑P0), `ui/navigation/AppNavigation.kt`, `ui/dashboard/DashboardScreen.kt` + `components/`, `ui/theme` (gradient/`FrostedCard`), `ActivityRing`, Canvas chart idioms.

## LEARN
- How the `@Singleton` session state machine works: states idle/activeSet/resting/completed, quick‑log, skip/prev, pause/resume, edit/delete, finish→duration; **rest‑timer end persisted to DataStore and restored across process death**.
- The foreground‑notification set‑logging subsystem (service + renderer + action receiver) and its perms.
- The workout Room schema + FK order (plans→days→planned_exercises→sessions→sets→PRs) and how `SyncManager` merges it (LWW, tombstones first).
- Where Wear coupling lives (to strip it cleanly).
- Flutter's superior designs to reproduce: plan **types** (weekly/monthly/custom + slot‑keys), **prefill‑from‑last‑set**, **animated‑GIF** exercise detail, **readiness‑aware** greeting, PR **max‑reps**.

## EXECUTE (small, ordered steps)
1. **Catalog:** copy `exercises.json` into `assets/`; add an `exercises` table + one‑time seeding on DB create/migration.
2. **Schema:** recreate the workout Room graph + DTOs under `com.hellohealth` conventions with `(userId, updatedAt, deletedAt, isSynced)`; write a real migration.
3. **Session core:** port `WorkoutSessionManager` (Coroutines/StateFlow) + DataStore rest‑timer resume; **strip Wear**. Rewrite DI to HelloHealth Hilt.
4. **Screens:** port live‑session screen + foreground service/notification; planner screens (**adopt Flutter plan‑type model**); exercise search + detail (**Coil‑gif** animated detail + YouTube launch).
5. **Use cases:** Start/Log/Finish/Skip/Edit; PR update (**add max‑reps + Epley est‑1RM**, fix weight‑only).
6. **Sync:** register the workout tables in the P0 `SyncManager` FK‑ordered merge.
7. **Card:** add the Workout dashboard card (planned day or "Start Session", streak, last session, readiness chip) + nav route in `AppNavigation.kt`. Use the readiness chip from P3.
8. **(P5) Analytics:** port `ProgressAnalyticsEngine` + models + 4 analytics‑cache tables (compute off main thread → cache to Room → push‑mostly to Supabase); Progress screen reusing `ActivityRing`/Canvas; extract the muscle map as a **standalone tappable** Compose widget.

## TEST
- Unit: session state‑machine transitions; `FinishSession` duration; PR update (weight, reps, Epley); streak edges.
- Migration test (workout tables); sync round‑trip (offline write → LWW merge → no dupes).
- Manual: full live session **including app‑kill mid‑rest** (timer resumes); notification actions log sets.
- (P5) engine determinism vs known fixtures; UI perf (no main‑thread compute).

## REVISE (fix, don't inherit)
- BMR: read real `UserProfile` (not hardcoded 25/male).
- Drop the dead duplicate standalone engines; keep the inline engine outputs that actually render.
- Ensure workout calories + muscle stimulus are published into the shared day rollup for Nutrition + Wellness.
