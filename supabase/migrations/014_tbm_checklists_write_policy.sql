-- 014_tbm_checklists_write_policy.sql
--
-- TBM Admin UX 개선 묶음 (2026-05-20) — Change 2 의 일부.
--
-- 배경:
--   013_tbm_schema.sql 은 tbm_checklists 에 ENABLE ROW LEVEL SECURITY + SELECT 만
--   허용 (service_role 만 write). 이로 인해 admin/worker UI 가 체크박스 toggle 또는
--   "추가 작업 사항" 자유 입력 PATCH 를 시도해도 RLS 가 차단해 클라이언트에서
--   업데이트 자체가 도달하지 않음.
--
-- 변경:
--   UPDATE 정책 1개 — anon + authenticated 모두 허용 (v1.0 PoC parity, 013 의
--   SELECT 패턴과 동일 수준). is_checked 와 note 두 컬럼 모두 같은 정책으로 커버.
--
-- v1.1 강화 TODO:
--   - "정형 row (item_text != '추가 작업 사항') 는 leader_user_id 만 UPDATE 가능,
--     freetext row 는 같은 그룹의 active session 참여자도 UPDATE 가능" 으로 분기.
--   - 예시 정책 (참고):
--       USING (
--         EXISTS (
--           SELECT 1 FROM public.tbm_sessions s
--           WHERE s.session_id = tbm_checklists.session_id
--             AND (
--               s.leader_user_id = auth.uid()::text
--               OR (
--                 tbm_checklists.item_text = '추가 작업 사항'
--                 AND EXISTS (
--                   SELECT 1 FROM public.tbm_participants p
--                   WHERE p.session_id = s.session_id
--                     AND p.user_id = auth.uid()::text
--                 )
--               )
--             )
--         )
--       )
--   - 별도 phase 에서 진행.

DO $$
BEGIN
    BEGIN
        CREATE POLICY "tbm_checklists_update_v1_poc" ON public.tbm_checklists
            FOR UPDATE TO anon, authenticated
            USING (true) WITH CHECK (true);
    EXCEPTION WHEN duplicate_object THEN NULL; END;
END $$;
