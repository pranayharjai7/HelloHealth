-- ============================================================================
-- HelloHealth — Security Remediation (Pre-Play Store Audit)
-- ----------------------------------------------------------------------------
-- 1. HHA-001: Server-side account deletion procedure & foreign key cascade
-- 2. HHA-002: Server-side AI coaching rate limiting and usage tracking
-- 3. HHA-003: Hardened SECURITY DEFINER functions with search_path = ''
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
-- 2. Atomic procedure to delete all user-associated database records (HHA-001 & HHA-003)
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
-- 3. AI Usage Ledger & Atomic Rate Limiting / Quotas (HHA-002 & HHA-003)
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

NOTIFY pgrst, 'reload schema';
