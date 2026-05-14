-- ============================================
-- 009: Phase 3 fusion event types
-- 지게차+사람 IoU 충돌 위험 (FUSION-01, D-11).
-- '안전모 미착용' 은 006_seed_data.sql 에 이미 seed — 추가 불필요.
-- ON CONFLICT 로 idempotent (중복 실행 안전).
-- ============================================

INSERT INTO public.event_types (event_name) VALUES
    ('지게차 충돌 위험')
ON CONFLICT (event_name) DO NOTHING;
