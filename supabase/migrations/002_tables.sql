-- ============================================
-- 002: Tables
-- Supabase 마이그레이션 - 테이블 생성
-- 기존 users 테이블 → profiles (Supabase Auth 연동)
-- ============================================

-- ─── 1. profiles (기존 users → Supabase Auth 연동) ───
CREATE TABLE IF NOT EXISTS public.profiles (
    id          UUID REFERENCES auth.users(id) ON DELETE CASCADE PRIMARY KEY,
    user_id     VARCHAR(50) UNIQUE NOT NULL,       -- 기존 로그인 ID 유지 (앱 호환)
    password    VARCHAR(255),                       -- 기존 bcrypt 해시 (마이그레이션 후 제거 가능)
    name        VARCHAR(50),
    phone_num   VARCHAR(20),
    email       VARCHAR(100),
    user_role   VARCHAR(20) DEFAULT 'worker',
    group_id    INTEGER,
    profile_image_url VARCHAR(255),
    is_invite_checked BOOLEAN DEFAULT false,
    invite_code VARCHAR(13) UNIQUE,
    fcm_token   TEXT,
    created_at  TIMESTAMP DEFAULT now()
);

-- ─── 2. groups ───
CREATE TABLE IF NOT EXISTS public.groups (
    group_id    SERIAL PRIMARY KEY,
    invite_code VARCHAR(20) UNIQUE NOT NULL,
    manager_id  VARCHAR(50) NOT NULL,
    created_at  TIMESTAMP DEFAULT now()
);

-- ─── 3. group_members ───
CREATE TABLE IF NOT EXISTS public.group_members (
    group_id      INTEGER NOT NULL REFERENCES public.groups(group_id) ON DELETE CASCADE,
    user_id       VARCHAR(50),
    phone_number  VARCHAR(20) NOT NULL,
    member_status VARCHAR(20) DEFAULT 'PENDING'
        CHECK (member_status IN ('PENDING', 'ACTIVE', 'CANCELED')),
    joined_at     TIMESTAMP DEFAULT now(),
    invite_code   VARCHAR(20),
    invitee_name  VARCHAR(50),
    invited_role  VARCHAR(20),
    PRIMARY KEY (group_id, phone_number)
);

-- ─── 4. workplace ───
CREATE TABLE IF NOT EXISTS public.workplace (
    place_id      SERIAL PRIMARY KEY,
    place_name    VARCHAR(100) NOT NULL,
    address       VARCHAR(255),
    road_address  VARCHAR(255),
    admin_id      VARCHAR(50) NOT NULL,
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION
);

-- ─── 5. cameras ───
CREATE TABLE IF NOT EXISTS public.cameras (
    camera_id           SERIAL PRIMARY KEY,
    device_name         VARCHAR(100) NOT NULL,
    device_code         VARCHAR(50) UNIQUE,
    host_code           VARCHAR(50),
    host_id             VARCHAR(50),
    host_password       VARCHAR(255),
    last_comm_date      TIMESTAMP,
    status              VARCHAR(20),
    install_area        VARCHAR(100),
    direction           VARCHAR(50),
    shooting_interval   INTEGER,
    created_at          TIMESTAMP DEFAULT now(),
    operating_hours     VARCHAR(24) DEFAULT '000000000000000000000000',
    image_res_name      VARCHAR(50),
    group_id            INTEGER REFERENCES public.groups(group_id) ON DELETE SET NULL,
    live_url            TEXT,
    installation_address VARCHAR(255),
    environment_type    VARCHAR(20) DEFAULT '내부',
    live_url_detail     TEXT,
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION
);

-- ─── 6. event_types ───
CREATE TABLE IF NOT EXISTS public.event_types (
    id          SERIAL PRIMARY KEY,
    event_name  VARCHAR(50) UNIQUE NOT NULL
);

-- ─── 7. camera_events ───
CREATE TABLE IF NOT EXISTS public.camera_events (
    camera_id       INTEGER NOT NULL REFERENCES public.cameras(camera_id) ON DELETE CASCADE,
    event_type_id   INTEGER NOT NULL REFERENCES public.event_types(id) ON DELETE CASCADE,
    PRIMARY KEY (camera_id, event_type_id)
);

-- ─── 8. camera_captures ───
CREATE TABLE IF NOT EXISTS public.camera_captures (
    capture_id  SERIAL PRIMARY KEY,
    camera_id   INTEGER REFERENCES public.cameras(camera_id),
    image_url   TEXT NOT NULL,
    captured_at TIMESTAMP DEFAULT now(),
    event_type  VARCHAR(50)
);

-- ─── 9. detection_events ───
CREATE TABLE IF NOT EXISTS public.detection_events (
    event_id              SERIAL PRIMARY KEY,
    camera_id             INTEGER REFERENCES public.cameras(camera_id),
    device_name           VARCHAR(100),
    install_area          VARCHAR(100),
    installation_address  VARCHAR(255),
    live_url              TEXT,
    accuracy              DOUBLE PRECISION,
    status                VARCHAR(20) DEFAULT 'PENDING',
    detected_at           TIMESTAMP DEFAULT now(),
    risk_level            VARCHAR(20),
    type_id               INTEGER REFERENCES public.event_types(id) ON DELETE SET NULL,
    capture_id            INTEGER REFERENCES public.camera_captures(capture_id) ON DELETE SET NULL
);

-- ─── 10. action_requests ───
CREATE TABLE IF NOT EXISTS public.action_requests (
    request_id      SERIAL PRIMARY KEY,
    event_id        INTEGER NOT NULL REFERENCES public.detection_events(event_id) ON DELETE CASCADE,
    requester_id    VARCHAR(50),
    worker_id       VARCHAR(50),
    request_type    TEXT NOT NULL,
    request_title   TEXT NOT NULL,
    request_details TEXT NOT NULL,
    requested_at    TIMESTAMP DEFAULT now(),
    action_report   TEXT,
    completed_at    TIMESTAMP
);

-- ─── 11. action_images ───
CREATE TABLE IF NOT EXISTS public.action_images (
    image_id    SERIAL PRIMARY KEY,
    request_id  INTEGER NOT NULL REFERENCES public.action_requests(request_id) ON DELETE CASCADE,
    image_url   TEXT NOT NULL
);

-- ─── 12. devices ───
CREATE TABLE IF NOT EXISTS public.devices (
    device_id       SERIAL PRIMARY KEY,
    device_type     VARCHAR(20) NOT NULL,
    serial_number   VARCHAR(50) UNIQUE NOT NULL,
    battery_level   INTEGER DEFAULT 100,
    gps_status      VARCHAR(20) DEFAULT 'OFF',
    user_id         VARCHAR(50),
    updated_at      TIMESTAMP DEFAULT now()
);

-- ─── 13. device_watches ───
CREATE TABLE IF NOT EXISTS public.device_watches (
    device_id   INTEGER PRIMARY KEY REFERENCES public.devices(device_id) ON DELETE CASCADE,
    body_temp   DOUBLE PRECISION DEFAULT 36.5,
    heart_rate  INTEGER DEFAULT 70
);

-- ─── 14. device_helmets ───
CREATE TABLE IF NOT EXISTS public.device_helmets (
    device_id       INTEGER PRIMARY KEY REFERENCES public.devices(device_id) ON DELETE CASCADE,
    unworn_count    INTEGER DEFAULT 0
);

-- ─── 15. fire_detectors ───
CREATE TABLE IF NOT EXISTS public.fire_detectors (
    detector_id     SERIAL PRIMARY KEY,
    group_id        INTEGER,
    detector_name   VARCHAR(100) NOT NULL,
    is_active       BOOLEAN DEFAULT true,
    status          VARCHAR(20) DEFAULT '정상',
    last_update     TIMESTAMP DEFAULT now()
);

-- ─── 16. arc_breakers ───
CREATE TABLE IF NOT EXISTS public.arc_breakers (
    breaker_id      SERIAL PRIMARY KEY,
    group_id        INTEGER NOT NULL,
    breaker_name    VARCHAR(100) NOT NULL,
    status          VARCHAR(20) DEFAULT 'NORMAL',
    status_msg      VARCHAR(255) DEFAULT '정상 작동 중',
    is_connected    BOOLEAN DEFAULT true,
    last_event_at   TIMESTAMP DEFAULT now()
);

-- ─── 17. device_event_logs ───
CREATE TABLE IF NOT EXISTS public.device_event_logs (
    log_id      BIGSERIAL PRIMARY KEY,
    device_type VARCHAR(20) NOT NULL,
    device_id   INTEGER NOT NULL,
    group_id    INTEGER NOT NULL,
    event_type  VARCHAR(30),
    created_at  TIMESTAMP DEFAULT now()
);

-- ─── 18. daily_safety_check ───
CREATE TABLE IF NOT EXISTS public.daily_safety_check (
    check_id        SERIAL PRIMARY KEY,
    writer_id       VARCHAR(50) NOT NULL,
    worker_id       VARCHAR(50),
    location        VARCHAR(255) NOT NULL,
    hazard          TEXT,
    countermeasure  TEXT,
    status          VARCHAR(20) DEFAULT '미점검'
        CHECK (status IN ('미점검', '점검완료')),
    check_date      DATE,
    created_at      DATE DEFAULT CURRENT_DATE,
    check_content   TEXT
);

-- ─── 19. check_images ───
CREATE TABLE IF NOT EXISTS public.check_images (
    image_id    SERIAL PRIMARY KEY,
    check_id    INTEGER NOT NULL REFERENCES public.daily_safety_check(check_id) ON DELETE CASCADE,
    image_url   TEXT NOT NULL
);

-- ─── 20. location_logs ───
CREATE TABLE IF NOT EXISTS public.location_logs (
    log_id      SERIAL PRIMARY KEY,
    user_id     VARCHAR(50),
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    current_zone VARCHAR(100),
    camera_id   INTEGER REFERENCES public.cameras(camera_id),
    recorded_at TIMESTAMP DEFAULT now(),
    status      VARCHAR(20) DEFAULT '정상'
);

-- ─── 21. geofence_zones (PostGIS) ───
CREATE TABLE IF NOT EXISTS public.geofence_zones (
    zone_id     SERIAL PRIMARY KEY,
    zone_name   VARCHAR(100),
    boundary    GEOMETRY(POLYGON, 4326) NOT NULL,
    group_id    INTEGER
);

-- ─── 22. notifications ───
CREATE TABLE IF NOT EXISTS public.notifications (
    notification_id SERIAL PRIMARY KEY,
    user_id         VARCHAR(50) NOT NULL,
    title           VARCHAR(100) NOT NULL,
    content         TEXT NOT NULL,
    is_read         BOOLEAN DEFAULT false,
    created_at      TIMESTAMP DEFAULT now()
);

-- ─── FK: profiles → groups (순환 참조 방지 위해 후속 추가) ───
ALTER TABLE public.profiles
    ADD CONSTRAINT fk_profile_group
    FOREIGN KEY (group_id) REFERENCES public.groups(group_id) ON DELETE SET NULL;

-- ─── Trigger: 회원가입 시 profiles 자동 생성 ───
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, user_id, email, created_at)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'user_id', NEW.id::text),
        NEW.email,
        now()
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();
