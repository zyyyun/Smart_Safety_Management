-- ============================================
-- 008: event_types extension (LP-2 확장)
-- 화재·안전모 미착용 은 006_seed_data.sql 에 이미 seed.
-- 지게차·사람(혼잡도) 만 추가. ON CONFLICT 로 idempotent.
-- ============================================

INSERT INTO public.event_types (event_name) VALUES
    ('지게차 진입'),
    ('혼잡도 경고')
ON CONFLICT (event_name) DO NOTHING;
