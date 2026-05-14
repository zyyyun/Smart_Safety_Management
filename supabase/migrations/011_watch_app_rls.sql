-- ============================================
-- 011: Watch App Bridge RLS + Realtime publication
-- Phase 7 — per CONTEXT.md D-04 / D-04b (amended 2026-05-14)
--
-- Adds:
--   (A) DROP 003 의 device_watches_select USING (true) 정책 (Pitfall 4)
--   (B) safety_alerts / wear_state_events / device_watches SELECT 정책 narrowing
--       — v1.0 PoC = mac_address IS NOT NULL 패턴 (auth.uid() 미사용 — Firebase Auth 환경)
--       — v1.1 Auth 도입 시 즉시 USING (auth.uid() = ...) 으로 강화 필요 (Assumption A2)
--   (C) supabase_realtime publication 에 4 테이블 등록 — Realtime broadcast 활성화 (Pitfall 6)
--   (D) UPDATE 정책 = 추가 안 함 (페어링 + ack 모두 Edge Function 경유 — D-03 + D-04b)
--
-- 의존: 010_watch_pipeline.sql (4 테이블 + RLS ENABLE 완료)
-- 적용: supabase db push  (또는)  psql $DATABASE_URL -f 011_watch_app_rls.sql
-- 재실행 안전: DROP IF EXISTS + CREATE POLICY (PostgreSQL 12+ 가 IF NOT EXISTS 지원 안 함 →
--             DROP 후 CREATE)
-- ============================================

-- ============================================
-- (A) Pitfall 4 — 003 의 device_watches_select USING(true) 제거
--     003_rls_policies.sql 가 운영 DB 에 적용된 이력 있음. 로컬 파일 부재해도 정책은 존재.
-- ============================================
DROP POLICY IF EXISTS "device_watches_select" ON public.device_watches;

-- ============================================
-- (B) v1.0 PoC SELECT 정책 — anon + authenticated 모두 mac_address IS NOT NULL 인 device 의 행만
-- ============================================
DROP POLICY IF EXISTS "safety_alerts_select_v1_poc" ON public.safety_alerts;
CREATE POLICY "safety_alerts_select_v1_poc" ON public.safety_alerts
    FOR SELECT
    TO anon, authenticated
    USING (
        device_id IN (
            SELECT device_id FROM public.devices
            WHERE mac_address IS NOT NULL
        )
    );

DROP POLICY IF EXISTS "wear_state_events_select_v1_poc" ON public.wear_state_events;
CREATE POLICY "wear_state_events_select_v1_poc" ON public.wear_state_events
    FOR SELECT
    TO anon, authenticated
    USING (
        device_id IN (
            SELECT device_id FROM public.devices
            WHERE mac_address IS NOT NULL
        )
    );

DROP POLICY IF EXISTS "device_watches_select_v1_poc" ON public.device_watches;
CREATE POLICY "device_watches_select_v1_poc" ON public.device_watches
    FOR SELECT
    TO anon, authenticated
    USING (
        device_id IN (
            SELECT device_id FROM public.devices
            WHERE mac_address IS NOT NULL
        )
    );

-- devices 테이블도 paired 워치 status 조회용 SELECT 허용
DROP POLICY IF EXISTS "devices_select_paired_watch_v1_poc" ON public.devices;
CREATE POLICY "devices_select_paired_watch_v1_poc" ON public.devices
    FOR SELECT
    TO anon, authenticated
    USING (
        device_type = 'WATCH' AND mac_address IS NOT NULL
    );

-- ============================================
-- (C) Pitfall 6 — supabase_realtime publication 에 4 테이블 명시 추가
--     이미 추가된 경우 NOTICE 출력 후 무시 (재실행 안전)
-- ============================================
DO $$
BEGIN
    BEGIN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.safety_alerts;
    EXCEPTION WHEN duplicate_object THEN
        NULL;
    END;
    BEGIN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.wear_state_events;
    EXCEPTION WHEN duplicate_object THEN
        NULL;
    END;
    BEGIN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.device_watches;
    EXCEPTION WHEN duplicate_object THEN
        NULL;
    END;
    BEGIN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.devices;
    EXCEPTION WHEN duplicate_object THEN
        NULL;
    END;
END $$;

-- ============================================
-- 적용 명령
--   supabase db push                                    (권장)
--   psql "$DATABASE_URL" -f 011_watch_app_rls.sql      (대안)
-- ============================================
