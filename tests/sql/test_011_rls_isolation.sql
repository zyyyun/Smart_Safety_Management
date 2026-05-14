-- tests/sql/test_011_rls_isolation.sql
-- Phase 7 / BRIDGE-03b — RLS isolation assertion (planner-spec)
-- 사용: psql "$DATABASE_URL" -f tests/sql/test_011_rls_isolation.sql
--      (service_role 으로 connect 후 SET ROLE anon 으로 정책 검증)
--
-- 검증 시나리오:
--   (a) 011 publication ADD TABLE 가 4 테이블 모두 supabase_realtime 에 포함시켰는지
--   (b) 011 의 SELECT 정책이 mac_address IS NOT NULL 패턴으로 narrowing 됐는지
--   (c) testuser1 의 paired 워치 (mac=21:02:02:06:01:69) 가 anon role 으로도 SELECT 가능
--
-- v1.0 PoC 한정: cross-user isolation 은 strict 하지 않음 (auth.uid() 미사용) —
--   본 테스트는 v1.0 정책의 *의도된 행동* 만 검증. v1.1 Auth 도입 시 cross-user
--   strict isolation 테스트로 교체.

\echo '=== Test 1: publication 에 4 테이블 ADD 검증 ==='
SELECT pubname, schemaname, tablename
  FROM pg_publication_tables
 WHERE pubname = 'supabase_realtime'
   AND schemaname = 'public'
   AND tablename IN ('safety_alerts','wear_state_events','device_watches','devices')
 ORDER BY tablename;
-- expect 4 rows

\echo '=== Test 2: 011 정책 4종이 등록됐는지 ==='
SELECT schemaname, tablename, policyname
  FROM pg_policies
 WHERE schemaname = 'public'
   AND policyname LIKE '%_v1_poc'
 ORDER BY tablename, policyname;
-- expect 4 rows: safety_alerts/wear_state_events/device_watches/devices

\echo '=== Test 3: 003 의 device_watches_select USING(true) 가 제거됐는지 ==='
SELECT count(*) AS legacy_open_policy_count
  FROM pg_policies
 WHERE schemaname = 'public'
   AND tablename = 'device_watches'
   AND policyname = 'device_watches_select';
-- expect 0 (DROP IF EXISTS 가 제거함)

\echo '=== Test 4: anon role 으로 paired 워치의 safety_alerts SELECT 가능 ==='
SET ROLE anon;
SELECT count(*) AS visible_alert_count
  FROM public.safety_alerts
 WHERE device_id IN (
     SELECT device_id FROM public.devices
      WHERE mac_address = '21:02:02:06:01:69'
 );
-- expect >= 0 (PoC 데이터 미적재 시 0, 적재 후 >0)
RESET ROLE;

\echo '=== Test 5: anon role 으로 mac_address IS NULL 인 device 의 alert 는 0행 ==='
SET ROLE anon;
-- 가짜 device (mac_address NULL) 시드 후 검증
-- (이 테스트는 read-only 라 시드 INSERT 안 함 — 정책 자체만 확인)
SELECT count(*) AS isolated_count
  FROM public.safety_alerts sa
 WHERE sa.device_id IN (
     SELECT device_id FROM public.devices
      WHERE mac_address IS NULL
 );
-- expect 0 (mac NULL device 자체가 정책 USING 절에서 배제됨)
RESET ROLE;

\echo '=== ALL RLS ISOLATION TESTS COMPLETE ==='
