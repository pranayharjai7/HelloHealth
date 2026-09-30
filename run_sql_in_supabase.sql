-- ============================================================================
-- HelloHealth — offline-first sync support (P0)
-- ----------------------------------------------------------------------------
-- Idempotent, ADDITIVE-ONLY. Safe to run repeatedly against an existing
-- Supabase project. It never drops or rewrites data — it only:
--   1. Adds the `updated_at` / `deleted_at` columns the LWW + tombstone sync
--      logic depends on (where missing).
--   2. Installs a BEFORE-UPDATE trigger that stamps `updated_at = now()` on
--      every server-side row update, so pulls can compare freshness.
--   3. Adds the P0.5 onboarding vitals columns to `profiles` (gender, birth
--      date, height/weight, activity level, goal type + targets, unit
--      preference, has_onboarded) so onboarding survives reinstall/multi-device
--      via a full pull round-trip.
--
-- Conflict keys used by the client syncers (must already exist as PK/UNIQUE):
--   profiles                : id                           (ProfileSyncer)
--   activity_goals          : user_id                      (GoalsSyncer)
--   food_preferences        : user_id                      (FoodPrefsSyncer)
--   daily_health_snapshots  : (user_id, snapshot_date)     (SnapshotSyncer)
--
-- LWW clock: the client compares its local `updatedAtEpochMs` against the
-- server `updated_at`. Until this script has run, `updated_at` is absent and
-- the client treats a missing server timestamp as epoch 0 (remote never wins),
-- so sync safely degrades to push-only with no data loss FOR THE SYNC-METADATA
-- COLUMNS (`updated_at`/`deleted_at`).
--
-- ⚠ DEPLOYMENT ORDERING (P0.5 onboarding columns): this is NOT true for the
-- onboarding-vitals columns added below. `ProfileSyncer.toDto()` ALWAYS puts
-- gender/height_cm/…/has_onboarded in the upsert body, so a client build that
-- writes those fields against a schema still missing them gets PostgREST
-- PGRST204 and the ENTIRE profile upsert fails (the vitals never reach the
-- server; a later reinstall/multi-device pull then returns has_onboarded=false
-- and re-triggers onboarding). Therefore:
--   1. Run this script BEFORE shipping/enabling any client that populates the
--      onboarding fields.
--   2. After it runs, reload PostgREST's schema cache so the API sees the new
--      columns immediately (else upserts keep failing until the next reload):
--          NOTIFY pgrst, 'reload schema';
--      (Supabase also auto-reloads within ~seconds via its schema-change
--      trigger, but issue the NOTIFY to be deterministic.)
-- ============================================================================

-- --- Shared trigger function: stamp updated_at on every UPDATE --------------
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- --- profiles ---------------------------------------------------------------
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

-- Onboarding vitals (P0.5 Step 11). Additive + idempotent — the client (ProfileSyncer.ProfileSyncDto)
-- already reads/writes these snake_case columns. NOTE: unlike the metadata columns above, the client
-- ALWAYS pushes these fields, so this migration must run (and PostgREST reload) BEFORE a client that
-- writes them — see the DEPLOYMENT ORDERING note in the header. The pull-side nullable decode
-- (`?: "METRIC"`, `?: false`) only guards reads, not pushes.
-- Types mirror the DTO: String? -> text, Long? -> bigint, Double? -> double precision.
-- unit_preference / has_onboarded are NOT NULL with a default matching the client fallback, so
-- adding them to a table with existing rows backfills those rows safely.
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS gender text;
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS birth_date_epoch_day bigint;
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS height_cm double precision;
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS weight_kg double precision;
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS activity_level text;
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS goal_type text;
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS target_weight_kg double precision;
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS target_rate_kg_per_week double precision;
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS unit_preference text NOT NULL DEFAULT 'METRIC';
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS has_onboarded boolean NOT NULL DEFAULT false;
-- P1: mood-tint theme toggle. Client (ProfileSyncDto) always pushes is_dynamic_theme; NOT NULL
-- DEFAULT true backfills existing rows to the feature's default-on behavior.
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS is_dynamic_theme boolean NOT NULL DEFAULT true;

-- AI Coaching: consent flag (opt-in). Client (ProfileSyncDto) pushes ai_coaching_enabled; kept
-- NULLABLE (no default) so old rows read as "not yet opted in" and the client maps null -> false.
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS ai_coaching_enabled boolean;

DROP TRIGGER IF EXISTS trg_profiles_set_updated_at ON public.profiles;
CREATE TRIGGER trg_profiles_set_updated_at
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- --- activity_goals ---------------------------------------------------------
ALTER TABLE public.activity_goals
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE public.activity_goals
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

DROP TRIGGER IF EXISTS trg_activity_goals_set_updated_at ON public.activity_goals;
CREATE TRIGGER trg_activity_goals_set_updated_at
    BEFORE UPDATE ON public.activity_goals
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- --- food_preferences -------------------------------------------------------
ALTER TABLE public.food_preferences
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE public.food_preferences
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

DROP TRIGGER IF EXISTS trg_food_preferences_set_updated_at ON public.food_preferences;
CREATE TRIGGER trg_food_preferences_set_updated_at
    BEFORE UPDATE ON public.food_preferences
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- --- daily_health_snapshots -------------------------------------------------
-- Already has updated_at / created_at / last_synced_at; only add the tombstone
-- column and the trigger.
ALTER TABLE public.daily_health_snapshots
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE public.daily_health_snapshots
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

DROP TRIGGER IF EXISTS trg_daily_health_snapshots_set_updated_at ON public.daily_health_snapshots;
CREATE TRIGGER trg_daily_health_snapshots_set_updated_at
    BEFORE UPDATE ON public.daily_health_snapshots
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- --- emotion_records (P1) ---------------------------------------------------
-- NEW table (does not exist yet) — multi-row per user, conflict key `id` (a
-- client-generated deterministic string "userId|timestampMs|EMOTION", matching
-- EmotionsSyncer.EmotionSyncDto). Unlike the tables above, this whole block is
-- additive-new, so it is created in full rather than ALTER-ed. Columns mirror
-- the DTO 1:1 (snake_case): text/int/double/nullable per the Kotlin types.
--   updated_at / deleted_at carry the same LWW-clock + tombstone semantics as
--   the other tables; the set_updated_at trigger stamps updated_at on UPDATE.
-- The client ALWAYS pushes every column, so this must run (and PostgREST
-- reload) BEFORE shipping the P1 client — else emotion upserts return PGRST204.
CREATE TABLE IF NOT EXISTS public.emotion_records (
    id            text PRIMARY KEY,
    user_id       text NOT NULL,
    timestamp_utc timestamptz NOT NULL,
    tz_offset     integer NOT NULL DEFAULT 0,
    local_date    text NOT NULL,
    emotion       text NOT NULL,
    confidence    double precision NOT NULL DEFAULT 1.0,
    source        text NOT NULL DEFAULT 'manual',
    note          text,
    visibility    text NOT NULL DEFAULT 'private',
    updated_at    timestamptz NOT NULL DEFAULT now(),
    deleted_at    timestamptz
);
-- Pull filters by user_id; index it for the per-user select.
CREATE INDEX IF NOT EXISTS idx_emotion_records_user_id ON public.emotion_records (user_id);

DROP TRIGGER IF EXISTS trg_emotion_records_set_updated_at ON public.emotion_records;
CREATE TRIGGER trg_emotion_records_set_updated_at
    BEFORE UPDATE ON public.emotion_records
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- --- Remove the retired flat workout_sessions vertical ----------------------
-- The single flat workout_sessions feature (activity_type/title/calories/distance)
-- was superseded by the TrackMe-style planning hierarchy below and is being
-- removed cleanly (it never reached master; the client dropped its local table in
-- Room MIGRATION_8_9 and deleted its syncer/DTO). DROP the server table to match —
-- CASCADE also removes its trigger + RLS policy. Idempotent: IF EXISTS makes this a
-- no-op once the table is gone. Any pre-existing rows were test-only.
DROP TABLE IF EXISTS public.workout_sessions CASCADE;

-- ============================================================================
-- Workout / Training planning hierarchy (TrackMe-style)
-- ----------------------------------------------------------------------------
-- Three NEW synced tables forming WorkoutPlan -> WorkoutDay -> PlannedExercise,
-- each multi-row per user with conflict key `id` (client-generated random UUID
-- string, matching WorkoutPlanSyncer / WorkoutDaySyncer / PlannedExerciseSyncer).
-- Additive-new like emotion_records, so created in full rather than ALTER-ed.
-- Columns mirror each DTO 1:1 (snake_case); target_* are all nullable except
-- target_sets (Int NOT NULL, client default 3). The exercise CATALOG is NOT here —
-- it is a local-only, read-only asset seeded identically per device (no user_id,
-- no sync), so there is deliberately no `exercises` table.
--   updated_at / deleted_at carry the same LWW-clock + tombstone semantics as the
--   other tables; the set_updated_at trigger stamps updated_at on UPDATE.
-- The client ALWAYS pushes every column, so this must run (and PostgREST reload)
-- BEFORE relying on pull — else upserts return PGRST204. Until it runs, the syncers
-- degrade to push-only losslessly (updated_at/deleted_at are null).

-- --- workout_plans (routines) -----------------------------------------------
CREATE TABLE IF NOT EXISTS public.workout_plans (
    id           text PRIMARY KEY,
    user_id      text NOT NULL,
    name         text NOT NULL,
    is_active    boolean NOT NULL DEFAULT false,
    plan_type    text NOT NULL DEFAULT 'WEEKLY',
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    deleted_at   timestamptz
);
-- Pull filters by user_id; index it for the per-user select.
CREATE INDEX IF NOT EXISTS idx_workout_plans_user_id ON public.workout_plans (user_id);

DROP TRIGGER IF EXISTS trg_workout_plans_set_updated_at ON public.workout_plans;
CREATE TRIGGER trg_workout_plans_set_updated_at
    BEFORE UPDATE ON public.workout_plans
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- --- workout_days (workouts within a routine) -------------------------------
CREATE TABLE IF NOT EXISTS public.workout_days (
    id           text PRIMARY KEY,
    plan_id      text NOT NULL,
    user_id      text NOT NULL,
    slot_key     text NOT NULL,
    name         text NOT NULL,
    updated_at   timestamptz NOT NULL DEFAULT now(),
    deleted_at   timestamptz
);
-- Pull filters by user_id; index it for the per-user select.
CREATE INDEX IF NOT EXISTS idx_workout_days_user_id ON public.workout_days (user_id);

DROP TRIGGER IF EXISTS trg_workout_days_set_updated_at ON public.workout_days;
CREATE TRIGGER trg_workout_days_set_updated_at
    BEFORE UPDATE ON public.workout_days
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- --- planned_exercises (an exercise + targets, within a day) -----------------
CREATE TABLE IF NOT EXISTS public.planned_exercises (
    id                      text PRIMARY KEY,
    day_id                  text NOT NULL,
    user_id                 text NOT NULL,
    exercise_id             text NOT NULL,
    order_index             integer NOT NULL DEFAULT 0,
    target_sets             integer NOT NULL DEFAULT 3,
    target_reps             integer,
    target_weight_kg        double precision,
    target_duration_seconds integer,
    target_distance_km      double precision,
    target_speed_kmh        double precision,
    target_incline          double precision,
    updated_at              timestamptz NOT NULL DEFAULT now(),
    deleted_at              timestamptz
);
-- Pull filters by user_id; index it for the per-user select.
CREATE INDEX IF NOT EXISTS idx_planned_exercises_user_id ON public.planned_exercises (user_id);

DROP TRIGGER IF EXISTS trg_planned_exercises_set_updated_at ON public.planned_exercises;
CREATE TRIGGER trg_planned_exercises_set_updated_at
    BEFORE UPDATE ON public.planned_exercises
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- ============================================================================
-- Row Level Security (owner-scoped) — P1 hardening
-- ----------------------------------------------------------------------------
-- The client uses real GoTrue auth (email + Google), so every PostgREST request
-- carries the user's JWT and `auth.uid()` is populated. Each table's ownership
-- column holds that same Supabase Auth UID:
--   profiles                : id        = auth.uid()
--   activity_goals          : user_id   = auth.uid()
--   food_preferences        : user_id   = auth.uid()
--   daily_health_snapshots  : user_id   = auth.uid()
--   emotion_records         : user_id   = auth.uid()
--   workout_plans           : user_id   = auth.uid()
--   workout_days            : user_id   = auth.uid()
--   planned_exercises       : user_id   = auth.uid()
-- Ownership-column types vary (emotion_records is `text`; the pre-existing P0
-- tables may be `uuid`), so we cast BOTH sides to text: `col::text = auth.uid()::text`.
-- This is `text = text` regardless of the column type — a no-op cast on text
-- columns, and the canonical lowercase-hyphenated form on uuid columns, which is
-- exactly what auth.uid()::text yields. Casting only auth.uid() failed with
-- "operator does not exist: uuid = text" against the uuid `id`/`user_id` columns.
--
-- Each table gets ONE permissive FOR ALL policy: USING gates which rows the
-- client may read/update/delete; WITH CHECK gates which rows it may insert/
-- update to — both to rows it owns. This matches the client's sync exactly
-- (it only ever pushes/pulls rows for getCurrentUserId()), so enabling RLS does
-- NOT lock out any legitimate write.
--
-- Idempotent: ENABLE ROW LEVEL SECURITY is a no-op if already on; each policy is
-- DROPped IF EXISTS then re-created (CREATE POLICY has no IF NOT EXISTS).
--
-- ⚠ RLS IS ONLY HALF THE GATE. Enabling RLS does not grant any base table
-- privilege — a role with no GRANT gets "permission denied for table ..." before
-- RLS is ever consulted. See the GRANT block AFTER the policies below; both must
-- run for a push/pull to succeed.
-- ----------------------------------------------------------------------------
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS profiles_owner ON public.profiles;
CREATE POLICY profiles_owner ON public.profiles
    FOR ALL TO authenticated
    USING (id::text = auth.uid()::text)
    WITH CHECK (id::text = auth.uid()::text);

ALTER TABLE public.activity_goals ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS activity_goals_owner ON public.activity_goals;
CREATE POLICY activity_goals_owner ON public.activity_goals
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text)
    WITH CHECK (user_id::text = auth.uid()::text);

ALTER TABLE public.food_preferences ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS food_preferences_owner ON public.food_preferences;
CREATE POLICY food_preferences_owner ON public.food_preferences
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text)
    WITH CHECK (user_id::text = auth.uid()::text);

ALTER TABLE public.daily_health_snapshots ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS daily_health_snapshots_owner ON public.daily_health_snapshots;
CREATE POLICY daily_health_snapshots_owner ON public.daily_health_snapshots
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text)
    WITH CHECK (user_id::text = auth.uid()::text);

ALTER TABLE public.emotion_records ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS emotion_records_owner ON public.emotion_records;
CREATE POLICY emotion_records_owner ON public.emotion_records
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text)
    WITH CHECK (user_id::text = auth.uid()::text);

ALTER TABLE public.workout_plans ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workout_plans_owner ON public.workout_plans;
CREATE POLICY workout_plans_owner ON public.workout_plans
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text)
    WITH CHECK (user_id::text = auth.uid()::text);

ALTER TABLE public.workout_days ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workout_days_owner ON public.workout_days;
CREATE POLICY workout_days_owner ON public.workout_days
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text)
    WITH CHECK (user_id::text = auth.uid()::text);

ALTER TABLE public.planned_exercises ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS planned_exercises_owner ON public.planned_exercises;
CREATE POLICY planned_exercises_owner ON public.planned_exercises
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text)
    WITH CHECK (user_id::text = auth.uid()::text);

-- ============================================================================
-- Table privileges for the `authenticated` role — REQUIRED alongside RLS
-- ----------------------------------------------------------------------------
-- RLS and GRANTs are TWO SEPARATE gates and BOTH must pass. Enabling RLS above
-- only decides WHICH ROWS a role may touch; it does NOT itself grant any table
-- privilege. Without an explicit GRANT, the `authenticated` role has no base
-- SELECT/INSERT/UPDATE right, so PostgREST returns:
--     "permission denied for table <t>
--      (Grant the required privileges ... GRANT ... TO authenticated;)"
-- and every push/pull fails even though the RLS policy would have allowed the row.
--
-- The pre-existing P0 tables received this grant when the project was first
-- provisioned (Supabase's default table privileges); the tables ADDED later by
-- this script (emotion_records, workout_plans/days, planned_exercises) did not —
-- hence they 403 until this block runs. Granting all is idempotent and
-- self-documenting.
--
-- We deliberately do NOT grant DELETE: the client never issues a hard DELETE — it
-- soft-deletes via a `deleted_at` tombstone (an UPDATE), so SELECT/INSERT/UPDATE
-- is the complete privilege set the sync path needs. Narrower = safer.
GRANT SELECT, INSERT, UPDATE ON public.profiles               TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.activity_goals         TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.food_preferences       TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.daily_health_snapshots TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.emotion_records        TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.workout_plans          TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.workout_days           TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.planned_exercises      TO authenticated;

-- --- Refresh PostgREST's schema cache ---------------------------------------
-- So the just-added profiles columns are visible to the API immediately and the
-- client's onboarding-field upserts stop returning PGRST204. Harmless to run
-- repeatedly; a no-op if PostgREST isn't the listener.
NOTIFY pgrst, 'reload schema';

-- ============================================================================
-- P3 — Vitals & Recovery: public.vitals_samples
-- ----------------------------------------------------------------------------
-- NEW table (does not exist yet) — multi-row per user, conflict key `id` (a
-- client-generated deterministic string: "userId|rollup|localDate" for the one
-- daily rollup row, or "userId|sample|timestampMs" for an intraday reading),
-- matching VitalsSampleSyncer.VitalsSampleDto. Additive-new, so created in full.
-- Columns mirror the DTO 1:1 (snake_case): the six vitals are `double precision`
-- (nullable — a device may lack any sensor), sleep minutes are nullable `integer`,
-- SpO2 is the natural 0-100 percentage (NOT a 0-1 fraction). updated_at/deleted_at
-- carry the same LWW-clock + tombstone semantics as the other tables; the
-- set_updated_at trigger stamps updated_at on UPDATE.
--
-- DEPLOY ORDER (critical): the client ALWAYS pushes every column, so this block
-- must run — and PostgREST must reload — BEFORE shipping the P3 client, or every
-- vitals upsert returns PGRST204 and the whole push fails.
CREATE TABLE IF NOT EXISTS public.vitals_samples (
    id                    text PRIMARY KEY,
    user_id               text NOT NULL,
    local_date            text NOT NULL,
    timestamp_utc         timestamptz NOT NULL,
    tz_offset             integer NOT NULL DEFAULT 0,
    kind                  text NOT NULL,
    resting_heart_rate    double precision,
    hrv_rmssd             double precision,
    respiratory_rate      double precision,
    body_temperature      double precision,
    hydration_ml          double precision,
    spo2                  double precision,
    sleep_duration_minutes integer,
    deep_sleep_minutes    integer,
    updated_at            timestamptz NOT NULL DEFAULT now(),
    deleted_at            timestamptz
);
-- Pull filters by user_id; index it for the per-user select.
CREATE INDEX IF NOT EXISTS idx_vitals_samples_user_id ON public.vitals_samples (user_id);

DROP TRIGGER IF EXISTS trg_vitals_samples_set_updated_at ON public.vitals_samples;
CREATE TRIGGER trg_vitals_samples_set_updated_at
    BEFORE UPDATE ON public.vitals_samples
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- RLS: owner-only, same shape as emotion_records (user_id is text).
ALTER TABLE public.vitals_samples ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS vitals_samples_owner ON public.vitals_samples;
CREATE POLICY vitals_samples_owner ON public.vitals_samples
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text)
    WITH CHECK (user_id::text = auth.uid()::text);

-- Table privileges (RLS + GRANT are separate gates; both must pass). No DELETE —
-- the client soft-deletes via a deleted_at tombstone (an UPDATE).
GRANT SELECT, INSERT, UPDATE ON public.vitals_samples TO authenticated;

-- Refresh PostgREST's schema cache so vitals_samples is visible to the API before
-- the client's first push. Harmless to run repeatedly.
NOTIFY pgrst, 'reload schema';

-- ============================================================================
-- P5 — Nutrition (HealthifyMe-style): public.nutrition_entries
-- ----------------------------------------------------------------------------
-- DROP LEGACY FIRST. The pre-existing `foods` + `food_logs` tables (0 rows, no
-- Kotlin references anywhere in the app) predate the sync contract and are not
-- RLS/trigger-compliant. They are replaced wholesale by nutrition_entries, so
-- drop them before creating the new table. CASCADE clears any dependent objects.
DROP TABLE IF EXISTS public.food_logs CASCADE;
DROP TABLE IF EXISTS public.foods CASCADE;

-- NEW table — the per-user food + water log, multi-row per user, conflict key
-- `id` (a client-generated random UUID string), matching
-- NutritionEntrySyncer.NutritionEntryDto 1:1 (snake_case). A single table carries
-- both kinds via the `kind` discriminator ('food'|'water') — a water row sets
-- water_ml + food_name='Water' + entry_method='water' with null macros, mirroring
-- the vitals_samples `kind` pattern (no separate water table). Macros
-- (protein/carbs/fat/fibre grams) and water_ml are nullable — a quick-add may
-- carry only calories, and a water row carries no macros. updated_at/deleted_at
-- carry the same LWW-clock + tombstone semantics as the other tables; the
-- set_updated_at trigger stamps updated_at on UPDATE.
--
-- The read-only USDA/OFF food catalog (cached_foods) is Room-only, per-device and
-- un-synced (like `exercises`) — it has NO Supabase table and NO syncer by design.
--
-- DEPLOY ORDER (critical): the client ALWAYS pushes every column, so this block
-- must run — and PostgREST must reload — BEFORE shipping the Nutrition client, or
-- every nutrition upsert returns PGRST204 and the whole push fails.
CREATE TABLE IF NOT EXISTS public.nutrition_entries (
    id             text PRIMARY KEY,
    user_id        text NOT NULL,
    local_date     text NOT NULL,
    timestamp_utc  timestamptz NOT NULL,
    tz_offset      integer NOT NULL DEFAULT 0,
    kind           text NOT NULL,
    meal_category  text,
    food_id        text,
    food_name      text NOT NULL,
    quantity       double precision NOT NULL,
    unit           text NOT NULL,
    calories       double precision NOT NULL,
    protein_g      double precision,
    carbs_g        double precision,
    fat_g          double precision,
    fibre_g        double precision,
    water_ml       double precision,
    entry_method   text NOT NULL,
    updated_at     timestamptz NOT NULL DEFAULT now(),
    deleted_at     timestamptz
);
-- Pull filters by user_id; index it for the per-user select.
CREATE INDEX IF NOT EXISTS idx_nutrition_entries_user_id ON public.nutrition_entries (user_id);

DROP TRIGGER IF EXISTS trg_nutrition_entries_set_updated_at ON public.nutrition_entries;
CREATE TRIGGER trg_nutrition_entries_set_updated_at
    BEFORE UPDATE ON public.nutrition_entries
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- RLS: owner-only, same shape as vitals_samples (user_id is text).
ALTER TABLE public.nutrition_entries ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS nutrition_entries_owner ON public.nutrition_entries;
CREATE POLICY nutrition_entries_owner ON public.nutrition_entries
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text)
    WITH CHECK (user_id::text = auth.uid()::text);

-- Table privileges (RLS + GRANT are separate gates; both must pass). No DELETE —
-- the client soft-deletes via a deleted_at tombstone (an UPDATE).
GRANT SELECT, INSERT, UPDATE ON public.nutrition_entries TO authenticated;

-- Refresh PostgREST's schema cache so nutrition_entries is visible to the API
-- before the client's first push. Harmless to run repeatedly.
NOTIFY pgrst, 'reload schema';
