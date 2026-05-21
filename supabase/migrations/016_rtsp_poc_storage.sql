-- ============================================
-- 016: RTSP PoC storage bucket (feature_rtps_test branch)
-- ============================================
-- 모바일 → Supabase Storage 청크 업로드 PoC.
-- design doc : .planning/explorations/2026-05-21_rtsp_mobile_relay_architecture.md (Approach 5)
-- plan       : ~/.claude/plans/feature-rtps-test-shimmering-fiddle.md (v3.1)
--
-- PoC 끝나면 bucket DROP 권장 (또는 별 down migration).
-- production 은 Edge Function `rtsp-poc-upload` + SYSTEM_AGENT_SECRET 으로 재구성 필수
-- (현재 anon INSERT 정책은 PoC trade-off).
-- ============================================

-- ─── rtsp-poc 버킷 ───
-- 모바일 frame sampler 가 cycle 마다 JPEG 1장 업로드.
-- public=false : anon read 차단. service_role (본부 PC scheduler) 만 read/delete.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'rtsp-poc',
    'rtsp-poc',
    false,
    2097152,                      -- 2MB (단일 JPEG 안전 마진)
    ARRAY['image/jpeg']
) ON CONFLICT (id) DO NOTHING;

-- ─── RLS 정책 ───

-- PoC: anon role 직접 INSERT 허용.
-- 본 앱은 Supabase Auth 가 아니라 자체 UserSession + Retrofit 흐름 (LogInActivity.kt:47)
-- 이라 모바일 client 가 anon key 만 보유. 따라서 `auth.role() = 'authenticated'`
-- 기반 정책 (004_storage.sql) 으로는 막힘. PoC 단계는 prefix + 확장자 sanity 만으로
-- 허용. 운영은 Edge Function 으로 교체 필수.
CREATE POLICY "rtsp_poc_anon_insert" ON storage.objects
    FOR INSERT
    WITH CHECK (
        bucket_id = 'rtsp-poc'
        AND (storage.foldername(name))[1] LIKE 'cam%'
        AND name LIKE '%.jpg'
    );

-- service_role 은 RLS 우회 → SELECT/DELETE policy 명시 불필요.
-- (본부 PC scheduler 는 SUPABASE_SERVICE_ROLE_KEY 로 list/download/delete.)

COMMENT ON POLICY "rtsp_poc_anon_insert" ON storage.objects IS
    'PoC: anon INSERT. production must replace with Edge Function rtsp-poc-upload + SYSTEM_AGENT_SECRET.';
