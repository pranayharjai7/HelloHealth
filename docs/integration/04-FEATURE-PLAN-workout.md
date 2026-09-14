# 04 — Feature Plan: Workout / Training (from TrackMe)

Bring TrackMe's gym system into HelloHealth as the **Workout / Training** hero card
on the Dashboard. Kotlin TrackMe is the code source of truth; a few TrackMe‑flutter
design wins are reproduced (see [`02`](02-TRACKME-KOTLIN-VS-FLUTTER-DIFF.md)).
**Depends on P0** (offline‑first spine). Wear OS is explicitly **out of scope** for now
(see Deferred below).

> **Status — SHIPPED (planning hierarchy).** The first slice below is built, tested,
> and synced. Everything under **Deferred to future phases** is documented here but NOT
> built yet. This doc is the source of truth for what exists vs. what is planned.

---

## SHIPPED — the planning hierarchy (first slice)

A three‑level model over a read‑only global exercise catalog:

```
WorkoutPlan (routine)  →  WorkoutDay (a workout)  →  PlannedExercise (+ targets)
                              over a read-only  Exercise  catalog (873 entries)
```

- **Plan / routine**: `name`, `isActive`, `planType` (WEEKLY / MONTHLY / CUSTOM). Only one
  plan is active at a time; the dashboard hero card summarizes it.
- **Day / workout**: `slotKey` — WEEKLY→MONDAY..SUNDAY, MONTHLY→D01..D31, CUSTOM→C01..C99 —
  plus a free `name`. Add‑day picker offers only unused slots for the plan's type.
- **PlannedExercise**: `exerciseId`, `orderIndex`, and the full target vocabulary
  `targetSets`(=3) / `targetReps?` / `targetWeightKg?` / `targetDurationSeconds?` /
  `targetDistanceKm?` / `targetSpeedKmh?` / `targetIncline?`. The target editor shows only
  the fields relevant to the exercise's runtime‑derived `LoggingType`.
- **Exercise catalog**: 873 entries seeded once from bundled `assets/exercises.json`
  (free‑exercise‑db). Global, read‑only, **not user‑owned and not synced** — seeded
  identically per device. `gifUrl` is built at load and images stream remotely via Coil
  (only the ~1 MB JSON is bundled). `LoggingType` (WEIGHTED_REPS / BODYWEIGHT_REPS / TIMED /
  CARDIO) is **derived at runtime** from category/equipment — never stored, so no column to
  migrate when the derivation improves.

### What was built
- **Domain**: `WorkoutPlan`, `WorkoutDay`, `PlannedExercise`, `Exercise`, `PlanType`
  (+`slotKeysFor`), `LoggingType` (+pure `deriveLoggingType`), `PlannedExerciseWithDetails`.
- **Room v9**: 4 entities (3 user tables with the 4 Syncable columns last; `exercises` with
  none), `MIGRATION_8_9` (DROPs the retired flat `workout_sessions`, CREATEs the 4 tables +
  indices), schema exported to `app/schemas/.../9.json`, `MigrationTest` 8→9.
- **Repositories**: `WorkoutPlanRepository` (owns the 3 user tables; create/list/setActive/
  rename/delete for plans+days+planned, add/edit‑targets/reorder; cascade‑delete tombstones
  parent+children atomically in one `@Transaction`), `ExerciseRepository` (local‑only:
  `seedIfEmpty`/`search`/`getById`/`getByIds`/`getAllCategories`).
- **Seeding**: `ExerciseAssetLoader` (parse wrapped in `runCatching` → parse failure is a
  defined result, count stays 0, next launch retries) + count‑gated `ExerciseSeeder`
  triggered from `MainActivity.onCreate` (covers fresh install AND migrated v8→v9 users).
- **Sync**: `WorkoutPlanSyncer` / `WorkoutDaySyncer` / `PlannedExerciseSyncer` (EmotionsSyncer
  template — snake_case DTOs, LWW pull, tombstone push), one `@Binds @IntoSet` each. Supabase
  tables `workout_plans` / `workout_days` / `planned_exercises` with owner RLS + GRANT (both
  gates) in `run_sql_in_supabase.sql`. The catalog has no Supabase table.
- **UI** (`ui/workoutplan/`): `RoutinesScreen`, `RoutineDetailScreen`, `DayDetailScreen`,
  `ExercisePickerScreen` (debounced search + category chips), `ExerciseDetailScreen`
  (gif + instructions + LoggingType‑driven target editor). Dashboard hero
  `WorkoutPlanCard` + its own `WorkoutPlanViewModel` (summary: active plan name / day count /
  planned‑exercise count).

### Verified
- Unit: migration 8→9 structural diff; repo create/list/cascade‑delete/user‑gating; seeding
  idempotency + parse‑failure leaves count 0; syncer LWW + tombstone push + version‑exact
  `markSynced`.
- On‑device (74ed1bb5): catalog seeds; hero card → Routines → create WEEKLY routine → add
  MONDAY day → Add exercise → search "bench" → pick → set targets → row shows gif + "3 sets ×
  8 reps"; full row round‑tripped to Supabase intact (local → Room → syncer → PostgREST).

---

## Deferred to future phases (documented, NOT built)

These are intentionally out of the first slice. Additive seams are already in place so each
phase = new entity + DAO + repo + syncer + one `@Binds @IntoSet` + one CREATE‑only migration +
one Supabase block — **never a reshape** of what shipped. New tables reference
`planned_exercises.id` / `exercises.id` by id.

1. **Live session logging.** `WorkoutSession → SessionSet` from a planned day: a `@Singleton`
   session state machine (idle / activeSet / resting / completed), quick‑log, skip/prev,
   pause/resume, edit/delete, finish→duration, **prefill‑from‑last‑set**. A foreground service
   + notification for set‑logging with the app backgrounded, and a **rest‑timer persisted to
   DataStore and restored across process death**. Strip TrackMe's Wear coupling on the way in.
2. **Personal records.** Epley est‑1RM **and** max‑reps per exercise (fix TrackMe's weight‑only
   PRs). Fix the **hardcoded BMR (25/male)** to read the real `UserProfile` vitals captured at
   onboarding (see [`09`](09-FEATURE-PLAN-onboarding-profile.md)).
3. **Whole‑app analytics engine.** Broader than TrackMe's: combine workouts + mood + vitals +
   nutrition into weekly muscle stimulus, fatigue, 1RM projection, TDEE, plateau detection,
   readiness score, etc. Compute off the main thread → cache to Room → push‑mostly to Supabase.
   Reuse `ActivityRing`/Canvas idioms; extract the muscle map as a standalone tappable widget.
   Publish workout calories + muscle stimulus into the shared day rollup for Nutrition + Wellness.
4. **Wearables.** Wear OS companion — the phone remains the only network writer; the watch is a
   remote for the live session. Revisit only after live logging exists.

> **Removed from the roadmap:** an earlier draft invented a **GPS / live‑cardio / route‑tracking**
> phase for this feature. TrackMe has no GPS or location tracking, so that phase was never real
> and is deleted. (The Google Maps *route on the read‑only Health Connect Activity detail screen*
> is a separate, existing feature and is unaffected.)

---

## Design principles carried in from TrackMe‑flutter
Plan **types** (weekly/monthly/custom + slot‑keys) ✅ shipped; **animated‑GIF** exercise detail
✅ shipped; **prefill‑from‑last‑set** → deferred (live logging); **readiness‑aware** greeting →
deferred (analytics); PR **max‑reps** → deferred (PRs).
