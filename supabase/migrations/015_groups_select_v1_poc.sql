-- 015_groups_select_v1_poc.sql
--
-- TBM Admin UX 개선 묶음 (2026-05-20) — Change 1 의 일부.
--
-- 배경:
--   003_rls_policies.sql:48-49 의 groups_select_own 정책이
--   `group_id = get_my_group_id()` 로 JWT claim 기반 필터. PoC v1.0 환경은
--   anon key 사용 (BuildConfig.SUPABASE_ANON_KEY) — JWT 에 group_id claim
--   없어 SELECT 가 0 row 반환. 결과: TBM dashboard 의 "그룹 선택" 영역이
--   "그룹 로드 중..." 에서 무한 정지 (사용자 보고 2026-05-20).
--
--   tbm_sessions / tbm_templates / tbm_checklists / tbm_participants 는
--   이미 013_tbm_schema.sql 에서 `SELECT USING (true)` 의 anon parity 적용.
--   groups 만 누락 — 본 마이그레이션이 동일 파리티 적용.
--
-- 변경:
--   groups SELECT 정책 추가 — anon + authenticated 모두 USING (true).
--   기존 groups_select_own (group_id = get_my_group_id()) 정책은 그대로 유지
--   (Postgres 는 여러 SELECT 정책을 OR 로 결합 — anon 도 OK, JWT 보유자도 OK).
--
-- v1.1 강화 TODO:
--   - groups.manager_id = auth.uid()::text 인 그룹만 admin 에게 표시.
--   - 또는 별도 manager_groups 조인 테이블로 admin↔group ACL 정의.
--   - 현재는 PoC parity 로 모든 그룹 표시 (시연/검단·포천 2 사이트 안전 마진).

DO $$
BEGIN
    BEGIN
        CREATE POLICY "groups_select_v1_poc" ON public.groups
            FOR SELECT TO anon, authenticated
            USING (true);
    EXCEPTION WHEN duplicate_object THEN NULL; END;
END $$;
