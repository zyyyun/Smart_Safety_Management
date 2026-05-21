-- ============================================
-- 006: Seed Data
-- 기본 이벤트 타입 데이터
-- ============================================

INSERT INTO public.event_types (event_name) VALUES
    ('쓰러짐'),
    ('추락'),
    ('화재'),
    ('안전모 미착용'),
    ('제한구역 침입'),
    ('이상행동')
ON CONFLICT (event_name) DO NOTHING;
