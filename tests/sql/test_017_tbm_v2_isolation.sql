-- tests/sql/test_017_tbm_v2_isolation.sql
-- Phase 12 TBM v2 schema regression guard.
-- Usage: psql "$DATABASE_URL" -f tests/sql/test_017_tbm_v2_isolation.sql

\echo '=== Test 1: TBM v2 tables have RLS enabled ==='
DO $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT count(*) INTO v_count
      FROM pg_class
     WHERE relnamespace = 'public'::regnamespace
       AND relname IN ('tbm_sessions', 'tbm_templates', 'tbm_checklists', 'tbm_participants')
       AND relrowsecurity = true;

    IF v_count <> 4 THEN
        RAISE EXCEPTION 'expected 4 RLS-enabled tbm tables, got %', v_count;
    END IF;
END $$;

\echo '=== Test 2: session uniqueness allows multiple work scopes per group/day ==='
DO $$
DECLARE
    v_exists BOOLEAN;
BEGIN
    SELECT true INTO v_exists
      FROM pg_constraint
     WHERE conname = 'tbm_sessions_group_date_scope_uq'
       AND conrelid = 'public.tbm_sessions'::regclass;

    IF v_exists IS DISTINCT FROM true THEN
        RAISE EXCEPTION 'missing tbm_sessions_group_date_scope_uq';
    END IF;
END $$;

\echo '=== Test 3: plating OPS seed templates exist ==='
DO $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT count(*) INTO v_count
      FROM public.tbm_templates
     WHERE work_type IN ('forklift', 'chemical', 'hot_work')
       AND is_active = true
       AND is_custom = false;

    IF v_count <> 3 THEN
        RAISE EXCEPTION 'expected 3 active built-in OPS templates, got %', v_count;
    END IF;
END $$;

\echo '=== Test 4: v2 template JSONB columns exist ==='
DO $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT count(*) INTO v_count
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'tbm_templates'
       AND column_name IN ('hazards', 'controls', 'key_actions', 'checks')
       AND data_type = 'jsonb';

    IF v_count <> 4 THEN
        RAISE EXCEPTION 'expected 4 jsonb template columns, got %', v_count;
    END IF;
END $$;

\echo '=== Test 5: templates remain SELECT-only for anon/authenticated ==='
DO $$
DECLARE
    v_write_policy_count INTEGER;
BEGIN
    SELECT count(*) INTO v_write_policy_count
      FROM pg_policies
     WHERE schemaname = 'public'
       AND tablename = 'tbm_templates'
       AND cmd IN ('INSERT', 'UPDATE', 'DELETE', 'ALL')
       AND roles && ARRAY['anon'::name, 'authenticated'::name];

    IF v_write_policy_count <> 0 THEN
        RAISE EXCEPTION 'tbm_templates has % anon/auth write policies', v_write_policy_count;
    END IF;
END $$;

\echo '=== Test 6: missed attendance cron and SECURITY DEFINER function exist ==='
DO $$
DECLARE
    v_active BOOLEAN;
    v_prosecdef BOOLEAN;
BEGIN
    SELECT active INTO v_active
      FROM cron.job
     WHERE jobname = 'tbm_missed_attendance_minute';

    IF v_active IS DISTINCT FROM true THEN
        RAISE EXCEPTION 'tbm_missed_attendance_minute is not active';
    END IF;

    SELECT prosecdef INTO v_prosecdef
      FROM pg_proc p
      JOIN pg_namespace n ON n.oid = p.pronamespace
     WHERE n.nspname = 'public'
       AND p.proname = 'tbm_missed_attendance_check';

    IF v_prosecdef IS DISTINCT FROM true THEN
        RAISE EXCEPTION 'tbm_missed_attendance_check is not SECURITY DEFINER';
    END IF;
END $$;

\echo '=== Test 7: signature storage bucket is preserved ==='
DO $$
DECLARE
    v_public BOOLEAN;
BEGIN
    SELECT public INTO v_public
      FROM storage.buckets
     WHERE id = 'tbm-signatures';

    IF v_public IS DISTINCT FROM false THEN
        RAISE EXCEPTION 'tbm-signatures bucket missing or public=true';
    END IF;
END $$;

\echo '=== Test 8: realtime publication contains four TBM tables ==='
DO $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT count(*) INTO v_count
      FROM pg_publication_tables
     WHERE pubname = 'supabase_realtime'
       AND schemaname = 'public'
       AND tablename IN ('tbm_sessions', 'tbm_templates', 'tbm_checklists', 'tbm_participants');

    IF v_count <> 4 THEN
        RAISE EXCEPTION 'expected 4 TBM realtime tables, got %', v_count;
    END IF;
END $$;

SELECT 'test_017_tbm_v2_isolation: 8/8 assertions PASS' AS result;
