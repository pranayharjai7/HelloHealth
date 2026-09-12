-- ============================================================================
-- HelloHealth — offline-first sync support (P0)
-- ----------------------------------------------------------------------------
-- Idempotent, ADDITIVE-ONLY. Safe to run repeatedly against an existing
-- Supabase project. It never drops or rewrites data — it only:
--   1. Adds the `updated_at` / `deleted_at` columns the LWW + tombstone sync
--      logic depends on (where missing).
--   2. Installs a BEFORE-UPDATE trigger that stamps `updated_at = now()` on
--      every server-side row update, so pulls can compare freshness.
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
-- so sync safely degrades to push-only with no data loss.
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
