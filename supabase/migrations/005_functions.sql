-- ============================================
-- 005: Database Functions
-- PostGIS 지오펜싱 + 유틸리티 함수
-- ============================================

-- ─── 지오펜스: 좌표가 어느 구역에 속하는지 확인 ───
CREATE OR REPLACE FUNCTION public.check_zone_containment(
    p_lat DOUBLE PRECISION,
    p_lng DOUBLE PRECISION,
    p_group_id INTEGER
)
RETURNS TEXT AS $$
    SELECT zone_name
    FROM public.geofence_zones
    WHERE group_id = p_group_id
      AND ST_Contains(boundary, ST_SetSRID(ST_Point(p_lng, p_lat), 4326))
    LIMIT 1;
$$ LANGUAGE sql STABLE;

-- ─── 지오펜스: 구역 목록 조회 (폴리곤 좌표 포함) ───
CREATE OR REPLACE FUNCTION public.get_geofence_zones_with_points(p_group_id INTEGER)
RETURNS TABLE (
    zone_id INTEGER,
    zone_name VARCHAR,
    group_id INTEGER,
    points JSON
) AS $$
    SELECT
        z.zone_id,
        z.zone_name,
        z.group_id,
        COALESCE(
            (SELECT json_agg(
                json_build_object(
                    'latitude', ST_Y(dp.geom),
                    'longitude', ST_X(dp.geom)
                )
            )
            FROM ST_DumpPoints(ST_ExteriorRing(z.boundary)) dp
            WHERE (dp.path)[1] < ST_NPoints(ST_ExteriorRing(z.boundary))),
            '[]'::json
        ) AS points
    FROM public.geofence_zones z
    WHERE z.group_id = p_group_id
    ORDER BY z.zone_id ASC;
$$ LANGUAGE sql STABLE;

-- ─── 지오펜스: 구역 생성 (WKT 텍스트 입력) ───
CREATE OR REPLACE FUNCTION public.create_geofence_zone(
    p_group_id INTEGER,
    p_zone_name VARCHAR,
    p_wkt TEXT
)
RETURNS INTEGER AS $$
    INSERT INTO public.geofence_zones (group_id, zone_name, boundary)
    VALUES (p_group_id, p_zone_name, ST_GeomFromText(p_wkt, 4326))
    RETURNING zone_id;
$$ LANGUAGE sql;

-- ─── 지오펜스: 구역 수정 ───
CREATE OR REPLACE FUNCTION public.update_geofence_zone(
    p_zone_id INTEGER,
    p_zone_name VARCHAR,
    p_wkt TEXT
)
RETURNS VOID AS $$
    UPDATE public.geofence_zones
    SET zone_name = p_zone_name,
        boundary = ST_GeomFromText(p_wkt, 4326)
    WHERE zone_id = p_zone_id;
$$ LANGUAGE sql;

-- ─── 위치 업데이트 + 구역 판별 (단일 트랜잭션) ───
CREATE OR REPLACE FUNCTION public.upsert_worker_location(
    p_user_id VARCHAR,
    p_lat DOUBLE PRECISION,
    p_lng DOUBLE PRECISION,
    p_group_id INTEGER,
    p_status VARCHAR DEFAULT '정상'
)
RETURNS TABLE (
    log_id INTEGER,
    current_zone VARCHAR
) AS $$
DECLARE
    v_zone VARCHAR;
    v_log_id INTEGER;
BEGIN
    -- 구역 판별
    SELECT gz.zone_name INTO v_zone
    FROM public.geofence_zones gz
    WHERE gz.group_id = p_group_id
      AND ST_Contains(gz.boundary, ST_SetSRID(ST_Point(p_lng, p_lat), 4326))
    LIMIT 1;

    -- 기존 로그 삭제 후 새로 삽입 (최신 위치만 유지)
    DELETE FROM public.location_logs WHERE user_id = p_user_id;

    INSERT INTO public.location_logs (user_id, latitude, longitude, current_zone, status)
    VALUES (p_user_id, p_lat, p_lng, v_zone, p_status)
    RETURNING location_logs.log_id INTO v_log_id;

    RETURN QUERY SELECT v_log_id, v_zone;
END;
$$ LANGUAGE plpgsql;

-- ─── 오래된 캡처 정리 (카메라당 최근 N개만 유지) ───
CREATE OR REPLACE FUNCTION public.cleanup_old_captures(
    p_camera_id INTEGER,
    p_keep_count INTEGER DEFAULT 5
)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    WITH old_captures AS (
        SELECT capture_id, image_url
        FROM public.camera_captures
        WHERE camera_id = p_camera_id
          AND event_type = 'PERIODIC'
        ORDER BY captured_at DESC
        OFFSET p_keep_count
    )
    DELETE FROM public.camera_captures
    WHERE capture_id IN (SELECT capture_id FROM old_captures);

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- ─── 초대코드 생성 유틸 ───
CREATE OR REPLACE FUNCTION public.generate_invite_code()
RETURNS VARCHAR AS $$
    SELECT substr(md5(random()::text || clock_timestamp()::text), 1, 13);
$$ LANGUAGE sql;

-- ─── 인덱스 ───
CREATE INDEX IF NOT EXISTS idx_profiles_group_id ON public.profiles(group_id);
CREATE INDEX IF NOT EXISTS idx_profiles_user_id ON public.profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_cameras_group_id ON public.cameras(group_id);
CREATE INDEX IF NOT EXISTS idx_detection_events_camera_id ON public.detection_events(camera_id);
CREATE INDEX IF NOT EXISTS idx_detection_events_detected_at ON public.detection_events(detected_at DESC);
CREATE INDEX IF NOT EXISTS idx_location_logs_user_id ON public.location_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON public.notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_daily_check_writer ON public.daily_safety_check(writer_id);
CREATE INDEX IF NOT EXISTS idx_daily_check_date ON public.daily_safety_check(check_date);
CREATE INDEX IF NOT EXISTS idx_geofence_group_id ON public.geofence_zones(group_id);
CREATE INDEX IF NOT EXISTS idx_device_event_logs_group ON public.device_event_logs(group_id);
