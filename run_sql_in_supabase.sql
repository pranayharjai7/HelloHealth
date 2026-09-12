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

-- --- Refresh PostgREST's schema cache ---------------------------------------
-- So the just-added profiles columns are visible to the API immediately and the
-- client's onboarding-field upserts stop returning PGRST204. Harmless to run
-- repeatedly; a no-op if PostgREST isn't the listener.
NOTIFY pgrst, 'reload schema';
