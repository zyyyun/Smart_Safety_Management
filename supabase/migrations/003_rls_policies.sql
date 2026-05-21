-- ============================================
-- 003: Row Level Security (RLS) 정책
-- group_id 기반 접근 제어
-- MANAGER: 그룹 전체 데이터 조회
-- WORKER: 본인 데이터만 접근
-- ============================================

-- ─── Helper Function: 현재 유저의 group_id 반환 ───
CREATE OR REPLACE FUNCTION public.get_my_group_id()
RETURNS INTEGER AS $$
    SELECT group_id FROM public.profiles WHERE id = auth.uid();
$$ LANGUAGE sql STABLE SECURITY DEFINER;

-- ─── Helper Function: 현재 유저의 user_id 반환 ───
CREATE OR REPLACE FUNCTION public.get_my_user_id()
RETURNS VARCHAR AS $$
    SELECT user_id FROM public.profiles WHERE id = auth.uid();
$$ LANGUAGE sql STABLE SECURITY DEFINER;

-- ─── Helper Function: 현재 유저의 role 반환 ───
CREATE OR REPLACE FUNCTION public.get_my_role()
RETURNS VARCHAR AS $$
    SELECT user_role FROM public.profiles WHERE id = auth.uid();
$$ LANGUAGE sql STABLE SECURITY DEFINER;

-- ============================================
-- profiles
-- ============================================
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "profiles_select_same_group" ON public.profiles
    FOR SELECT USING (
        group_id = get_my_group_id()
        OR id = auth.uid()
    );

CREATE POLICY "profiles_update_own" ON public.profiles
    FOR UPDATE USING (id = auth.uid());

CREATE POLICY "profiles_insert_own" ON public.profiles
    FOR INSERT WITH CHECK (id = auth.uid());

-- ============================================
-- groups
-- ============================================
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;

CREATE POLICY "groups_select_own" ON public.groups
    FOR SELECT USING (group_id = get_my_group_id());

CREATE POLICY "groups_insert_manager" ON public.groups
    FOR INSERT WITH CHECK (get_my_role() IN ('manager', 'general_manager'));

CREATE POLICY "groups_update_manager" ON public.groups
    FOR UPDATE USING (
        group_id = get_my_group_id()
        AND get_my_role() IN ('manager', 'general_manager')
    );

-- ============================================
-- group_members
-- ============================================
ALTER TABLE public.group_members ENABLE ROW LEVEL SECURITY;

CREATE POLICY "group_members_select_same_group" ON public.group_members
    FOR SELECT USING (group_id = get_my_group_id());

CREATE POLICY "group_members_insert_manager" ON public.group_members
    FOR INSERT WITH CHECK (
        group_id = get_my_group_id()
        AND get_my_role() IN ('manager', 'general_manager')
    );

CREATE POLICY "group_members_update_same_group" ON public.group_members
    FOR UPDATE USING (group_id = get_my_group_id());

CREATE POLICY "group_members_delete_manager" ON public.group_members
    FOR DELETE USING (
        group_id = get_my_group_id()
        AND get_my_role() IN ('manager', 'general_manager')
    );

-- ============================================
-- workplace
-- ============================================
ALTER TABLE public.workplace ENABLE ROW LEVEL SECURITY;

CREATE POLICY "workplace_select_own" ON public.workplace
    FOR SELECT USING (admin_id = get_my_user_id());

CREATE POLICY "workplace_insert_manager" ON public.workplace
    FOR INSERT WITH CHECK (get_my_role() IN ('manager', 'general_manager'));

CREATE POLICY "workplace_update_own" ON public.workplace
    FOR UPDATE USING (admin_id = get_my_user_id());

CREATE POLICY "workplace_delete_own" ON public.workplace
    FOR DELETE USING (admin_id = get_my_user_id());

-- ============================================
-- cameras (group_id 기반)
-- ============================================
ALTER TABLE public.cameras ENABLE ROW LEVEL SECURITY;

CREATE POLICY "cameras_select_same_group" ON public.cameras
    FOR SELECT USING (group_id = get_my_group_id());

CREATE POLICY "cameras_insert_manager" ON public.cameras
    FOR INSERT WITH CHECK (
        group_id = get_my_group_id()
        AND get_my_role() IN ('manager', 'general_manager')
    );

CREATE POLICY "cameras_update_manager" ON public.cameras
    FOR UPDATE USING (
        group_id = get_my_group_id()
        AND get_my_role() IN ('manager', 'general_manager')
    );

CREATE POLICY "cameras_delete_manager" ON public.cameras
    FOR DELETE USING (
        group_id = get_my_group_id()
        AND get_my_role() IN ('manager', 'general_manager')
    );

-- ============================================
-- camera_captures (카메라의 group 기반)
-- ============================================
ALTER TABLE public.camera_captures ENABLE ROW LEVEL SECURITY;

CREATE POLICY "camera_captures_select" ON public.camera_captures
    FOR SELECT USING (
        camera_id IN (SELECT camera_id FROM public.cameras WHERE group_id = get_my_group_id())
    );

-- ============================================
-- detection_events (카메라의 group 기반)
-- ============================================
ALTER TABLE public.detection_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY "detection_events_select" ON public.detection_events
    FOR SELECT USING (
        camera_id IN (SELECT camera_id FROM public.cameras WHERE group_id = get_my_group_id())
    );

-- ============================================
-- action_requests
-- ============================================
ALTER TABLE public.action_requests ENABLE ROW LEVEL SECURITY;

CREATE POLICY "action_requests_select" ON public.action_requests
    FOR SELECT USING (
        requester_id = get_my_user_id()
        OR worker_id = get_my_user_id()
        OR get_my_role() IN ('manager', 'general_manager')
    );

CREATE POLICY "action_requests_insert" ON public.action_requests
    FOR INSERT WITH CHECK (true);

-- ============================================
-- devices (user_id 기반)
-- ============================================
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;

CREATE POLICY "devices_select_same_group" ON public.devices
    FOR SELECT USING (
        user_id = get_my_user_id()
        OR user_id IN (
            SELECT p.user_id FROM public.profiles p
            WHERE p.group_id = get_my_group_id()
        )
    );

-- ============================================
-- daily_safety_check
-- ============================================
ALTER TABLE public.daily_safety_check ENABLE ROW LEVEL SECURITY;

CREATE POLICY "daily_check_select" ON public.daily_safety_check
    FOR SELECT USING (
        writer_id = get_my_user_id()
        OR worker_id = get_my_user_id()
        OR writer_id IN (
            SELECT p.user_id FROM public.profiles p
            WHERE p.group_id = get_my_group_id()
        )
    );

CREATE POLICY "daily_check_insert" ON public.daily_safety_check
    FOR INSERT WITH CHECK (true);

CREATE POLICY "daily_check_update" ON public.daily_safety_check
    FOR UPDATE USING (
        writer_id = get_my_user_id()
        OR worker_id = get_my_user_id()
    );

CREATE POLICY "daily_check_delete" ON public.daily_safety_check
    FOR DELETE USING (writer_id = get_my_user_id());

-- ============================================
-- location_logs
-- ============================================
ALTER TABLE public.location_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY "location_logs_select" ON public.location_logs
    FOR SELECT USING (
        user_id = get_my_user_id()
        OR (get_my_role() IN ('manager', 'general_manager')
            AND user_id IN (
                SELECT p.user_id FROM public.profiles p
                WHERE p.group_id = get_my_group_id()
            ))
    );

CREATE POLICY "location_logs_insert" ON public.location_logs
    FOR INSERT WITH CHECK (user_id = get_my_user_id());

-- ============================================
-- geofence_zones
-- ============================================
ALTER TABLE public.geofence_zones ENABLE ROW LEVEL SECURITY;

CREATE POLICY "geofence_select_same_group" ON public.geofence_zones
    FOR SELECT USING (group_id = get_my_group_id());

CREATE POLICY "geofence_insert_manager" ON public.geofence_zones
    FOR INSERT WITH CHECK (
        group_id = get_my_group_id()
        AND get_my_role() IN ('manager', 'general_manager')
    );

CREATE POLICY "geofence_update_manager" ON public.geofence_zones
    FOR UPDATE USING (
        group_id = get_my_group_id()
        AND get_my_role() IN ('manager', 'general_manager')
    );

CREATE POLICY "geofence_delete_manager" ON public.geofence_zones
    FOR DELETE USING (
        group_id = get_my_group_id()
        AND get_my_role() IN ('manager', 'general_manager')
    );

-- ============================================
-- notifications
-- ============================================
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;

CREATE POLICY "notifications_select_own" ON public.notifications
    FOR SELECT USING (user_id = get_my_user_id());

CREATE POLICY "notifications_update_own" ON public.notifications
    FOR UPDATE USING (user_id = get_my_user_id());

-- ============================================
-- 나머지 테이블 (RLS 활성화, 서비스 키로만 접근)
-- Edge Functions에서 service_role 키 사용
-- ============================================
ALTER TABLE public.event_types ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.camera_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.action_images ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.check_images ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_watches ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_helmets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fire_detectors ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.arc_breakers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_event_logs ENABLE ROW LEVEL SECURITY;

-- 읽기 전용 공개 테이블
CREATE POLICY "event_types_select_all" ON public.event_types
    FOR SELECT USING (true);

-- 그룹 기반 조회
CREATE POLICY "fire_detectors_select" ON public.fire_detectors
    FOR SELECT USING (group_id = get_my_group_id());

CREATE POLICY "arc_breakers_select" ON public.arc_breakers
    FOR SELECT USING (group_id = get_my_group_id());

CREATE POLICY "device_event_logs_select" ON public.device_event_logs
    FOR SELECT USING (group_id = get_my_group_id());

-- 이미지 테이블은 부모 테이블 접근 권한 따라감 (서비스 키 사용)
CREATE POLICY "check_images_select" ON public.check_images
    FOR SELECT USING (true);

CREATE POLICY "action_images_select" ON public.action_images
    FOR SELECT USING (true);

CREATE POLICY "camera_events_select" ON public.camera_events
    FOR SELECT USING (true);

CREATE POLICY "device_watches_select" ON public.device_watches
    FOR SELECT USING (true);

CREATE POLICY "device_helmets_select" ON public.device_helmets
    FOR SELECT USING (true);
