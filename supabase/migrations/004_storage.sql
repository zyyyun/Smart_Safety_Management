-- ============================================
-- 004: Supabase Storage 버킷 설정
-- 기존 로컬 public/uploads/ → Supabase Storage
-- ============================================

-- 프로필 이미지 버킷 (공개)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'profile-images',
    'profile-images',
    true,
    5242880,  -- 5MB
    ARRAY['image/jpeg', 'image/png', 'image/webp']
) ON CONFLICT (id) DO NOTHING;

-- 일일 안전점검 이미지 버킷 (공개)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'check-images',
    'check-images',
    true,
    10485760,  -- 10MB
    ARRAY['image/jpeg', 'image/png', 'image/webp']
) ON CONFLICT (id) DO NOTHING;

-- 조치 요청 이미지 버킷 (공개)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'action-images',
    'action-images',
    true,
    10485760,  -- 10MB
    ARRAY['image/jpeg', 'image/png', 'image/webp']
) ON CONFLICT (id) DO NOTHING;

-- CCTV 캡처 이미지 버킷 (공개)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'camera-captures',
    'camera-captures',
    true,
    10485760,  -- 10MB
    ARRAY['image/jpeg', 'image/png', 'image/webp']
) ON CONFLICT (id) DO NOTHING;

-- ─── Storage RLS 정책 ───

-- 프로필 이미지: 인증된 사용자 업로드/조회
CREATE POLICY "profile_images_select" ON storage.objects
    FOR SELECT USING (bucket_id = 'profile-images');

CREATE POLICY "profile_images_insert" ON storage.objects
    FOR INSERT WITH CHECK (
        bucket_id = 'profile-images'
        AND auth.role() = 'authenticated'
    );

CREATE POLICY "profile_images_update" ON storage.objects
    FOR UPDATE USING (
        bucket_id = 'profile-images'
        AND auth.role() = 'authenticated'
    );

-- 점검 이미지: 인증된 사용자 업로드/조회
CREATE POLICY "check_images_storage_select" ON storage.objects
    FOR SELECT USING (bucket_id = 'check-images');

CREATE POLICY "check_images_storage_insert" ON storage.objects
    FOR INSERT WITH CHECK (
        bucket_id = 'check-images'
        AND auth.role() = 'authenticated'
    );

-- 조치 이미지: 인증된 사용자 업로드/조회
CREATE POLICY "action_images_storage_select" ON storage.objects
    FOR SELECT USING (bucket_id = 'action-images');

CREATE POLICY "action_images_storage_insert" ON storage.objects
    FOR INSERT WITH CHECK (
        bucket_id = 'action-images'
        AND auth.role() = 'authenticated'
    );

-- CCTV 캡처: 서비스 키로만 업로드 (Edge Function), 인증 사용자 조회
CREATE POLICY "camera_captures_storage_select" ON storage.objects
    FOR SELECT USING (bucket_id = 'camera-captures');
