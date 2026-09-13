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

-- --- Refresh PostgREST's schema cache ---------------------------------------
-- So the just-added profiles columns are visible to the API immediately and the
-- client's onboarding-field upserts stop returning PGRST204. Harmless to run
-- repeatedly; a no-op if PostgREST isn't the listener.
NOTIFY pgrst, 'reload schema';
