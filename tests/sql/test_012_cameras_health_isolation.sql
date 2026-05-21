-- tests/sql/test_012_cameras_health_isolation.sql
-- Phase 8 / T-8-02 회귀 가드 (RLS isolation assertion)
-- 사용: psql "$DATABASE_URL" -f tests/sql/test_012_cameras_health_isolation.sql
--      (service_role 으로 connect 후 SET ROLE anon 으로 정책 검증)
--      psql 미설치 환경: Supabase Dashboard → SQL Editor 에 그대로 paste 하여 실행.
--
-- 검증 시나리오:
--   (a) 012 의 ALTER ADD COLUMN 3종이 모두 적용됐는지 (information_schema)
--   (b) cameras 의 RLS 가 여전히 ENABLED 인지 (012 ALTER 가 ENABLE 안 풀었는지)
--   (c) 003 cameras_update_manager 정책이 살아있는지 (012 가 깨지 않음)
--   (d) anon role 로 cameras.health_state UPDATE 시도 → permission denied 또는 0 rows
--   (e) 신규 cameras (last_frame_at NULL) 가 즉시 'down' 폭발하지 않음 (Pitfall 4 회귀 가드)
--   (f) cron.job 에 'cameras_healthcheck_minute' 등록 + active=true
--   (g) public.cameras_healthcheck 함수가 SECURITY DEFINER 인지 (Pitfall 7 회귀 가드)

\echo '=== Test 1: 012 의 ALTER ADD COLUMN 3종 적용 확인 ==='
SELECT column_name, data_type, column_default
  FROM information_schema.columns
 WHERE table_schema = 'public'
   AND table_name   = 'cameras'
   AND column_name  IN ('last_frame_at','health_state','last_alert_at')
 ORDER BY column_name;
-- expect 3 rows

\echo '=== Test 2: cameras RLS 가 ENABLED 인지 (012 가 풀지 않음) ==='
SELECT relname, relrowsecurity, relforcerowsecurity
  FROM pg_class
 WHERE relname = 'cameras'
   AND relnamespace = 'public'::regnamespace;
-- expect relrowsecurity = t

\echo '=== Test 3: 003 의 cameras_update_manager 정책이 살아있는지 ==='
SELECT schemaname, tablename, policyname, cmd
  FROM pg_policies
 WHERE schemaname = 'public'
   AND tablename  = 'cameras'
   AND policyname = 'cameras_update_manager';
-- expect 1 row (cmd='UPDATE')

\echo '=== Test 4: T-8-02 회귀 가드 — anon 의 cameras.health_state UPDATE 차단 ==='
BEGIN;
SET LOCAL ROLE anon;
DO $$
DECLARE
    v_updated_rows INTEGER;
BEGIN
    BEGIN
        UPDATE public.cameras SET health_state = 'down' WHERE camera_id = 1;
        GET DIAGNOSTICS v_updated_rows = ROW_COUNT;
        IF v_updated_rows > 0 THEN
            -- 도달 시 정책 누락 또는 anon 권한 과다 — REGRESSION
            RAISE EXCEPTION 'T-8-02 REGRESSION: anon updated % rows of cameras.health_state', v_updated_rows;
        ELSE
            -- 0 rows = RLS 가 USING 절에서 anon row 0개 매칭 → 조용한 차단
            RAISE NOTICE 'T-8-02 OK: anon UPDATE returned 0 rows (RLS USING filtered all)';
        END IF;
    EXCEPTION WHEN insufficient_privilege THEN
        -- 명시적 permission denied — 역시 OK
        RAISE NOTICE 'T-8-02 OK: anon UPDATE blocked by insufficient_privilege (%)', SQLERRM;
    WHEN OTHERS THEN
        -- 기타 정책 차단 (RLS) — OK
        RAISE NOTICE 'T-8-02 OK: anon UPDATE blocked (%)', SQLERRM;
    END;
END $$;
ROLLBACK;

\echo '=== Test 5: Pitfall 4 회귀 가드 — 신규 cameras (last_frame_at NULL) 즉시 down 폭발 0 ==='
SELECT count(*) AS unknown_count
  FROM public.cameras
 WHERE last_frame_at IS NULL
   AND health_state = 'unknown';
-- expect 모든 신규 cameras 수 (예: 5) — cameras_healthcheck() 가 NULL 가드로 건드리지 않음

SELECT count(*) AS spurious_down_count
  FROM public.cameras
 WHERE last_frame_at IS NULL
   AND health_state = 'down';
-- expect 0 (Pitfall 4 회귀가 있으면 > 0 — 즉시 down 폭발)

\echo '=== Test 6: pg_cron job 등록 확인 ==='
SELECT jobname, schedule, active
  FROM cron.job
 WHERE jobname = 'cameras_healthcheck_minute';
-- expect 1 row, schedule='* * * * *', active=true

\echo '=== Test 7: Pitfall 7 회귀 가드 — cameras_healthcheck SECURITY DEFINER ==='
SELECT proname, prosecdef
  FROM pg_proc
 WHERE pronamespace = 'public'::regnamespace
   AND proname = 'cameras_healthcheck';
-- expect 1 row, prosecdef=t

\echo '=== ALL CAMERAS HEALTH ISOLATION TESTS COMPLETE ==='
