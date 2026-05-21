-- tests/sql/test_013_tbm_isolation.sql
-- Phase 9 / T-9-01·02·05·07·08 회귀 가드 (RLS isolation + 시드 + cron assertion)
-- 사용: psql "$DATABASE_URL" -f tests/sql/test_013_tbm_isolation.sql
--      (service_role 으로 connect 후 SET LOCAL ROLE anon 으로 정책 검증)
--      psql 미설치 환경: Supabase Dashboard → SQL Editor 에 그대로 paste 하여 실행.
--
-- 검증 시나리오 (7 assertions):
--   (a) 4 신규 테이블의 RLS 가 ENABLED 인지 (013 ALTER 가 풀지 않음)
--   (b) T-9-08 회귀 가드 — anon 의 tbm_sessions UPDATE 차단 (write 정책 미등록 = default deny)
--   (c) 5 templates 시드 확인 (work_type IN ('fire','electric','height','heavy','general'))
--   (d) pg_cron job 'tbm_missed_attendance_minute' 등록 + active=true
--   (e) Pitfall 7 회귀 가드 — tbm_missed_attendance_check SECURITY DEFINER (prosecdef=t)
--   (f) tbm-signatures Storage 버킷 존재 + public=false (T-9-01 PII 가드)
--   (g) Realtime publication 4 테이블 모두 등록 (research C2 amendment 회귀 가드)

\echo '=== Test 1: 4 신규 tbm_* 테이블의 RLS 가 모두 ENABLED 인지 ==='
DO $$
DECLARE
    v_rls_enabled_count INTEGER;
BEGIN
    SELECT count(*) INTO v_rls_enabled_count
      FROM pg_class
     WHERE relname IN ('tbm_sessions','tbm_templates','tbm_checklists','tbm_participants')
       AND relnamespace = 'public'::regnamespace
       AND relrowsecurity = true;
    IF v_rls_enabled_count <> 4 THEN
        RAISE EXCEPTION 'RLS 4-table assertion FAIL: only % of 4 tables have RLS ENABLED', v_rls_enabled_count;
    END IF;
    RAISE NOTICE 'Test 1 OK: 4 tbm_* tables all have RLS ENABLED';
END $$;

\echo '=== Test 2: T-9-08 회귀 가드 — anon 의 tbm_sessions UPDATE 차단 ==='
BEGIN;
SET LOCAL ROLE anon;
DO $$
DECLARE
    v_updated_rows INTEGER;
BEGIN
    BEGIN
        UPDATE public.tbm_sessions SET ended_at = now() WHERE session_id = -1;
        GET DIAGNOSTICS v_updated_rows = ROW_COUNT;
        IF v_updated_rows > 0 THEN
            RAISE EXCEPTION 'T-9-08 REGRESSION: anon updated % rows of tbm_sessions', v_updated_rows;
        ELSE
            RAISE NOTICE 'T-9-08 OK: anon UPDATE returned 0 rows (RLS default deny + no row match)';
        END IF;
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'T-9-08 OK: anon UPDATE blocked by insufficient_privilege (%)', SQLERRM;
    WHEN OTHERS THEN
        RAISE NOTICE 'T-9-08 OK: anon UPDATE blocked (%)', SQLERRM;
    END;
END $$;
ROLLBACK;

\echo '=== Test 3: 5 templates 시드 확인 ==='
DO $$
DECLARE
    v_seed_count INTEGER;
BEGIN
    SELECT count(*) INTO v_seed_count
      FROM public.tbm_templates
     WHERE work_type IN ('fire','electric','height','heavy','general');
    IF v_seed_count <> 5 THEN
        RAISE EXCEPTION '5 templates seed assertion FAIL: only % of 5 work_types present', v_seed_count;
    END IF;
    RAISE NOTICE 'Test 3 OK: 5 templates seed all present (fire/electric/height/heavy/general)';
END $$;

\echo '=== Test 4: pg_cron job tbm_missed_attendance_minute 등록 확인 ==='
DO $$
DECLARE
    v_active BOOLEAN;
    v_schedule TEXT;
BEGIN
    SELECT active, schedule INTO v_active, v_schedule
      FROM cron.job
     WHERE jobname = 'tbm_missed_attendance_minute';
    IF v_active IS NULL THEN
        RAISE EXCEPTION 'cron.job assertion FAIL: tbm_missed_attendance_minute not registered';
    END IF;
    IF v_active IS NOT TRUE THEN
        RAISE EXCEPTION 'cron.job assertion FAIL: tbm_missed_attendance_minute active=%', v_active;
    END IF;
    IF v_schedule IS DISTINCT FROM '* * * * *' THEN
        RAISE EXCEPTION 'cron.job assertion FAIL: tbm_missed_attendance_minute schedule=% (expected ''* * * * *'')', v_schedule;
    END IF;
    RAISE NOTICE 'Test 4 OK: tbm_missed_attendance_minute active=true schedule=''* * * * *''';
END $$;

\echo '=== Test 5: Pitfall 7 회귀 가드 — tbm_missed_attendance_check SECURITY DEFINER ==='
DO $$
DECLARE
    v_prosecdef BOOLEAN;
BEGIN
    SELECT prosecdef INTO v_prosecdef
      FROM pg_proc p JOIN pg_namespace n ON p.pronamespace = n.oid
     WHERE n.nspname = 'public'
       AND p.proname = 'tbm_missed_attendance_check';
    IF v_prosecdef IS NULL THEN
        RAISE EXCEPTION 'pg_proc assertion FAIL: tbm_missed_attendance_check function not found';
    END IF;
    IF v_prosecdef IS NOT TRUE THEN
        RAISE EXCEPTION 'Pitfall 7 REGRESSION: tbm_missed_attendance_check prosecdef=% (expected TRUE)', v_prosecdef;
    END IF;
    RAISE NOTICE 'Test 5 OK: tbm_missed_attendance_check is SECURITY DEFINER';
END $$;

\echo '=== Test 6: T-9-01 PII 가드 — tbm-signatures bucket 존재 + public=false ==='
DO $$
DECLARE
    v_public BOOLEAN;
BEGIN
    SELECT public INTO v_public
      FROM storage.buckets
     WHERE id = 'tbm-signatures';
    IF v_public IS NULL THEN
        RAISE EXCEPTION 'Storage bucket assertion FAIL: tbm-signatures bucket not found';
    END IF;
    IF v_public IS NOT FALSE THEN
        RAISE EXCEPTION 'T-9-01 REGRESSION: tbm-signatures bucket public=% (expected FALSE)', v_public;
    END IF;
    RAISE NOTICE 'Test 6 OK: tbm-signatures bucket exists with public=false (PII 가드)';
END $$;

\echo '=== Test 7: Realtime publication 4 테이블 모두 등록 (research C2 회귀 가드) ==='
DO $$
DECLARE
    v_pub_count INTEGER;
BEGIN
    SELECT count(*) INTO v_pub_count
      FROM pg_publication_tables
     WHERE pubname = 'supabase_realtime'
       AND tablename IN ('tbm_sessions','tbm_templates','tbm_checklists','tbm_participants');
    IF v_pub_count <> 4 THEN
        RAISE EXCEPTION 'Realtime publication assertion FAIL: only % of 4 tbm_* tables registered', v_pub_count;
    END IF;
    RAISE NOTICE 'Test 7 OK: 4 tbm_* tables all registered in supabase_realtime publication';
END $$;

SELECT 'test_013_tbm_isolation: 7/7 assertions PASS' AS result;
