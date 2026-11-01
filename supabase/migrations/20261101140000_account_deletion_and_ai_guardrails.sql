-- ============================================================================
-- HelloHealth — Security Remediation (Pre-Play Store Audit)
-- ----------------------------------------------------------------------------
-- 1. HHA-001: Server-side account deletion procedure & foreign key cascade
-- 2. HHA-001: Tombstone table & active account verification (Stale JWT mitigation)
-- 3. HHA-001: Hardened RLS policies & defense-in-depth mutation triggers
-- 4. HHA-002: Server-side AI coaching rate limiting and usage tracking
-- 5. HHA-003: Hardened SECURITY DEFINER functions with search_path = ''
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Fix Foreign Key on nutrition_goals to CASCADE on auth deletion
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'nutrition_goals_user_id_fkey'
          AND table_name = 'nutrition_goals'
    ) THEN
        ALTER TABLE public.nutrition_goals
            DROP CONSTRAINT nutrition_goals_user_id_fkey;
    END IF;
END $$;

ALTER TABLE public.nutrition_goals
    ADD CONSTRAINT nutrition_goals_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- ----------------------------------------------------------------------------
-- 2. Tombstone table & account activity checker (Stale JWT mitigation)
-- ----------------------------------------------------------------------------
-- PostgREST validates access JWT signatures statelessly; already-issued access
-- tokens remain valid until their `exp` timestamp (~1 hour). For tables without
-- foreign keys to auth.users, stale tokens could recreate rows post-deletion.
-- The tombstone table and is_active_account() ensure all RLS policies and
-- triggers reject any operation from deleted or non-existent accounts immediately.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.deleted_accounts (
    user_id text PRIMARY KEY,
    deleted_at timestamptz NOT NULL DEFAULT pg_catalog.now()
);

ALTER TABLE public.deleted_accounts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.deleted_accounts FROM PUBLIC, anon, authenticated;
GRANT ALL ON public.deleted_accounts TO service_role;

CREATE OR REPLACE FUNCTION public.is_active_account(p_user_id text)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_uuid uuid;
    v_is_deleted boolean;
    v_exists boolean;
BEGIN
    IF p_user_id IS NULL OR pg_catalog.length(pg_catalog.trim(p_user_id)) = 0 THEN
        RETURN false;
    END IF;

    -- 1. Check tombstone table first (fast indexed lookup)
    SELECT EXISTS (
        SELECT 1 FROM public.deleted_accounts WHERE user_id = p_user_id
    ) INTO v_is_deleted;

    IF v_is_deleted THEN
        RETURN false;
    END IF;

    -- 2. Validate UUID format
    BEGIN
        v_user_uuid := p_user_id::uuid;
    EXCEPTION WHEN OTHERS THEN
        RETURN false;
    END;

    -- 3. Check if user actually exists in auth.users (fast indexed PK lookup)
    BEGIN
        SELECT EXISTS (
            SELECT 1 FROM auth.users WHERE id = v_user_uuid
        ) INTO v_exists;
    EXCEPTION WHEN OTHERS THEN
        v_exists := true;
    END;

    RETURN v_exists;
END;
$$;

REVOKE ALL ON FUNCTION public.is_active_account(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_active_account(text) TO authenticated, service_role, anon;

-- ----------------------------------------------------------------------------
-- 3. Atomic procedure to delete all user-associated database records (HHA-001 & HHA-003)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.delete_user_account(target_user_id uuid DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid;
    v_user_id_text text;
BEGIN
    -- Determine target user identity
    IF target_user_id IS NOT NULL THEN
        -- Only allow deleting another account if caller is service_role (auth.uid() is null in service role)
        IF auth.uid() IS NOT NULL AND auth.uid() <> target_user_id THEN
            RAISE EXCEPTION 'Not authorized to delete another user account';
        END IF;
        v_user_id := target_user_id;
    ELSE
        v_user_id := auth.uid();
    END IF;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Unauthenticated request';
    END IF;

    v_user_id_text := v_user_id::text;

    -- Record tombstone to immediately invalidate all current access JWTs and prevent row recreation
    INSERT INTO public.deleted_accounts (user_id, deleted_at)
    VALUES (v_user_id_text, pg_catalog.now())
    ON CONFLICT (user_id) DO UPDATE SET deleted_at = pg_catalog.now();

    -- 1. Workout planning & live sessions
    DELETE FROM public.session_sets WHERE user_id = v_user_id_text;
    DELETE FROM public.workout_sessions WHERE user_id = v_user_id_text;
    DELETE FROM public.planned_exercises WHERE user_id = v_user_id_text;
    DELETE FROM public.workout_days WHERE user_id = v_user_id_text;
    DELETE FROM public.workout_plans WHERE user_id = v_user_id_text;

    -- 2. Nutrition entries & goals
    DELETE FROM public.nutrition_entries WHERE user_id = v_user_id_text;
    DELETE FROM public.nutrition_goals WHERE user_id = v_user_id;

    -- 3. Body metrics, vitals, emotions
    DELETE FROM public.body_metrics WHERE user_id = v_user_id_text;
    DELETE FROM public.vitals_samples WHERE user_id = v_user_id_text;
    DELETE FROM public.emotion_records WHERE user_id = v_user_id_text;

    -- 4. Wellness, streaks, points, achievements
    DELETE FROM public.streaks WHERE user_id = v_user_id_text;
    DELETE FROM public.points_ledger WHERE user_id = v_user_id_text;
    DELETE FROM public.achievements WHERE user_id = v_user_id_text;

    -- 5. AI usage ledger
    DELETE FROM public.ai_usage_ledger WHERE user_id = v_user_id_text;

    -- 6. Daily snapshots, food preferences, activity goals
    DELETE FROM public.daily_health_snapshots WHERE user_id = v_user_id;
    DELETE FROM public.food_preferences WHERE user_id = v_user_id;
    DELETE FROM public.activity_goals WHERE user_id = v_user_id;

    -- 7. User profile
    DELETE FROM public.profiles WHERE id = v_user_id;
END;
$$;

REVOKE ALL ON FUNCTION public.delete_user_account(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.delete_user_account(uuid) TO authenticated, service_role;

-- ----------------------------------------------------------------------------
-- 4. Defense-in-depth trigger to prevent mutation from deleted/inactive accounts
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.prevent_deleted_account_mutation()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_target_user_id text;
BEGIN
    IF TG_TABLE_NAME = 'profiles' THEN
        v_target_user_id := NEW.id::text;
    ELSE
        v_target_user_id := NEW.user_id::text;
    END IF;

    IF v_target_user_id IS NOT NULL AND NOT public.is_active_account(v_target_user_id) THEN
        RAISE EXCEPTION 'Operation rejected: account % is inactive or has been deleted', v_target_user_id
            USING ERRCODE = 'data_exception';
    END IF;
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.prevent_deleted_account_mutation() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.prevent_deleted_account_mutation() TO authenticated, service_role;

-- Attach trigger to all 12 text-keyed tables
DROP TRIGGER IF EXISTS trg_prevent_deleted_mutation ON public.emotion_records;
CREATE TRIGGER trg_prevent_deleted_mutation
    BEFORE INSERT OR UPDATE ON public.emotion_records
    FOR EACH ROW EXECUTE FUNCTION public.prevent_deleted_account_mutation();

DROP TRIGGER IF EXISTS trg_prevent_deleted_mutation ON public.workout_plans;
CREATE TRIGGER trg_prevent_deleted_mutation
    BEFORE INSERT OR UPDATE ON public.workout_plans
    FOR EACH ROW EXECUTE FUNCTION public.prevent_deleted_account_mutation();

DROP TRIGGER IF EXISTS trg_prevent_deleted_mutation ON public.workout_days;
CREATE TRIGGER trg_prevent_deleted_mutation
    BEFORE INSERT OR UPDATE ON public.workout_days
    FOR EACH ROW EXECUTE FUNCTION public.prevent_deleted_account_mutation();

DROP TRIGGER IF EXISTS trg_prevent_deleted_mutation ON public.planned_exercises;
CREATE TRIGGER trg_prevent_deleted_mutation
    BEFORE INSERT OR UPDATE ON public.planned_exercises
    FOR EACH ROW EXECUTE FUNCTION public.prevent_deleted_account_mutation();

DROP TRIGGER IF EXISTS trg_prevent_deleted_mutation ON public.vitals_samples;
CREATE TRIGGER trg_prevent_deleted_mutation
    BEFORE INSERT OR UPDATE ON public.vitals_samples
    FOR EACH ROW EXECUTE FUNCTION public.prevent_deleted_account_mutation();

DROP TRIGGER IF EXISTS trg_prevent_deleted_mutation ON public.body_metrics;
CREATE TRIGGER trg_prevent_deleted_mutation
    BEFORE INSERT OR UPDATE ON public.body_metrics
    FOR EACH ROW EXECUTE FUNCTION public.prevent_deleted_account_mutation();

DROP TRIGGER IF EXISTS trg_prevent_deleted_mutation ON public.nutrition_entries;
CREATE TRIGGER trg_prevent_deleted_mutation
    BEFORE INSERT OR UPDATE ON public.nutrition_entries
    FOR EACH ROW EXECUTE FUNCTION public.prevent_deleted_account_mutation();

DROP TRIGGER IF EXISTS trg_prevent_deleted_mutation ON public.workout_sessions;
CREATE TRIGGER trg_prevent_deleted_mutation
    BEFORE INSERT OR UPDATE ON public.workout_sessions
    FOR EACH ROW EXECUTE FUNCTION public.prevent_deleted_account_mutation();

DROP TRIGGER IF EXISTS trg_prevent_deleted_mutation ON public.session_sets;
CREATE TRIGGER trg_prevent_deleted_mutation
    BEFORE INSERT OR UPDATE ON public.session_sets
    FOR EACH ROW EXECUTE FUNCTION public.prevent_deleted_account_mutation();

DROP TRIGGER IF EXISTS trg_prevent_deleted_mutation ON public.streaks;
CREATE TRIGGER trg_prevent_deleted_mutation
    BEFORE INSERT OR UPDATE ON public.streaks
    FOR EACH ROW EXECUTE FUNCTION public.prevent_deleted_account_mutation();

DROP TRIGGER IF EXISTS trg_prevent_deleted_mutation ON public.points_ledger;
CREATE TRIGGER trg_prevent_deleted_mutation
    BEFORE INSERT OR UPDATE ON public.points_ledger
    FOR EACH ROW EXECUTE FUNCTION public.prevent_deleted_account_mutation();

DROP TRIGGER IF EXISTS trg_prevent_deleted_mutation ON public.achievements;
CREATE TRIGGER trg_prevent_deleted_mutation
    BEFORE INSERT OR UPDATE ON public.achievements
    FOR EACH ROW EXECUTE FUNCTION public.prevent_deleted_account_mutation();

-- ----------------------------------------------------------------------------
-- 5. Hardened RLS policies across all 17 public tables (Stale JWT mitigation)
-- ----------------------------------------------------------------------------
-- Each policy requires caller to be the row owner AND verifies account is active.
-- If account was deleted, PostgREST queries with stale JWTs fail immediately.

DROP POLICY IF EXISTS profiles_owner ON public.profiles;
CREATE POLICY profiles_owner ON public.profiles
    FOR ALL TO authenticated
    USING (id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

DROP POLICY IF EXISTS activity_goals_owner ON public.activity_goals;
CREATE POLICY activity_goals_owner ON public.activity_goals
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

DROP POLICY IF EXISTS food_preferences_owner ON public.food_preferences;
CREATE POLICY food_preferences_owner ON public.food_preferences
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

DROP POLICY IF EXISTS daily_health_snapshots_owner ON public.daily_health_snapshots;
CREATE POLICY daily_health_snapshots_owner ON public.daily_health_snapshots
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

DROP POLICY IF EXISTS nutrition_goals_owner ON public.nutrition_goals;
CREATE POLICY nutrition_goals_owner ON public.nutrition_goals
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

DROP POLICY IF EXISTS emotion_records_owner ON public.emotion_records;
CREATE POLICY emotion_records_owner ON public.emotion_records
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

DROP POLICY IF EXISTS workout_plans_owner ON public.workout_plans;
CREATE POLICY workout_plans_owner ON public.workout_plans
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

DROP POLICY IF EXISTS workout_days_owner ON public.workout_days;
CREATE POLICY workout_days_owner ON public.workout_days
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

DROP POLICY IF EXISTS planned_exercises_owner ON public.planned_exercises;
CREATE POLICY planned_exercises_owner ON public.planned_exercises
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

DROP POLICY IF EXISTS vitals_samples_owner ON public.vitals_samples;
CREATE POLICY vitals_samples_owner ON public.vitals_samples
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

DROP POLICY IF EXISTS body_metrics_owner ON public.body_metrics;
CREATE POLICY body_metrics_owner ON public.body_metrics
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

DROP POLICY IF EXISTS nutrition_entries_owner ON public.nutrition_entries;
CREATE POLICY nutrition_entries_owner ON public.nutrition_entries
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

DROP POLICY IF EXISTS workout_sessions_owner ON public.workout_sessions;
CREATE POLICY workout_sessions_owner ON public.workout_sessions
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

DROP POLICY IF EXISTS session_sets_owner ON public.session_sets;
CREATE POLICY session_sets_owner ON public.session_sets
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

DROP POLICY IF EXISTS streaks_owner ON public.streaks;
CREATE POLICY streaks_owner ON public.streaks
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

DROP POLICY IF EXISTS points_ledger_owner ON public.points_ledger;
CREATE POLICY points_ledger_owner ON public.points_ledger
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

DROP POLICY IF EXISTS achievements_owner ON public.achievements;
CREATE POLICY achievements_owner ON public.achievements
    FOR ALL TO authenticated
    USING (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text))
    WITH CHECK (user_id::text = auth.uid()::text AND public.is_active_account(auth.uid()::text));

-- ----------------------------------------------------------------------------
-- 6. AI Usage Ledger & Atomic Rate Limiting / Quotas (HHA-002 & HHA-003)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.ai_usage_ledger (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id text NOT NULL,
    requested_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_usage_ledger_user_time
    ON public.ai_usage_ledger(user_id, requested_at);

ALTER TABLE public.ai_usage_ledger ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION public.check_and_record_ai_usage(
    p_user_id text,
    p_max_per_minute integer DEFAULT 5,
    p_max_per_day integer DEFAULT 30
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_minute_count integer;
    v_day_count integer;
BEGIN
    IF p_user_id IS NULL OR pg_catalog.length(pg_catalog.trim(p_user_id)) = 0 THEN
        RETURN pg_catalog.jsonb_build_object(
            'allowed', false,
            'reason', 'invalid_user_id'
        );
    END IF;

    -- Enforce that caller's JWT matches the target user_id (if authenticated)
    IF auth.uid() IS NOT NULL AND auth.uid()::text <> p_user_id THEN
        RETURN pg_catalog.jsonb_build_object(
            'allowed', false,
            'reason', 'unauthorized_user_id'
        );
    END IF;

    -- Enforce that caller's account is active and not deleted
    IF NOT public.is_active_account(p_user_id) THEN
        RETURN pg_catalog.jsonb_build_object(
            'allowed', false,
            'reason', 'account_deleted_or_inactive'
        );
    END IF;

    -- Concurrency protection: serialize quota checks per user for the transaction duration
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(p_user_id));

    -- Count requests in the last 60 seconds
    SELECT COUNT(*) INTO v_minute_count
    FROM public.ai_usage_ledger
    WHERE user_id = p_user_id
      AND requested_at >= pg_catalog.now() - interval '1 minute';

    IF v_minute_count >= p_max_per_minute THEN
        RETURN pg_catalog.jsonb_build_object(
            'allowed', false,
            'reason', 'rate_limit_exceeded',
            'retry_after_seconds', 60
        );
    END IF;

    -- Count requests in the last 24 hours
    SELECT COUNT(*) INTO v_day_count
    FROM public.ai_usage_ledger
    WHERE user_id = p_user_id
      AND requested_at >= pg_catalog.now() - interval '24 hours';

    IF v_day_count >= p_max_per_day THEN
        RETURN pg_catalog.jsonb_build_object(
            'allowed', false,
            'reason', 'daily_quota_exceeded',
            'retry_after_seconds', 3600
        );
    END IF;

    -- Record request
    INSERT INTO public.ai_usage_ledger (user_id, requested_at)
    VALUES (p_user_id, pg_catalog.now());

    -- Opportunistic cleanup of entries older than 48 hours
    DELETE FROM public.ai_usage_ledger
    WHERE requested_at < pg_catalog.now() - interval '48 hours';

    RETURN pg_catalog.jsonb_build_object(
        'allowed', true,
        'minute_count', v_minute_count + 1,
        'day_count', v_day_count + 1
    );
END;
$$;

REVOKE ALL ON FUNCTION public.check_and_record_ai_usage(text, integer, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.check_and_record_ai_usage(text, integer, integer) TO authenticated, service_role;

-- ----------------------------------------------------------------------------
-- 7. Restrictive Storage policy to block stale-token uploads across all buckets
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = 'storage' AND table_name = 'objects'
    ) THEN
        EXECUTE $pol$
            DROP POLICY IF EXISTS storage_prevent_deleted_account_mutation ON storage.objects;
            CREATE POLICY storage_prevent_deleted_account_mutation ON storage.objects
                AS RESTRICTIVE
                FOR ALL TO authenticated
                USING (public.is_active_account(auth.uid()::text))
                WITH CHECK (public.is_active_account(auth.uid()::text));
        $pol$;
    END IF;
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;

NOTIFY pgrst, 'reload schema';
