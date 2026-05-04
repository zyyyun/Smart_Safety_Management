-- ============================================
-- 010: Watch J2208A 1-person pipeline schema
-- Phase 4 — per CONTEXT.md D-01 / D-02 / D-03 / D-14
--
-- Adds:
--   (A) ALTER public.devices : mac_address / firmware_version / last_comm_at
--   (B) public.raw_events           — raw 패킷, 7일 TTL, 1초 dedup (UNIQUE generated cols)
--   (C) public.wear_state_events    — wear-state 전이 로그, 영구
--   (D) public.minute_summary       — 1인 × 1분 × 1행 집계, 영구
--   (E) public.safety_alerts        — 위험 알림, 영구
--   (F) RLS ENABLE on 4 신규 테이블 — 정책 미등록 = service_role 전용 (D-03)
--   (G) pg_cron job 'cleanup_raw_events_hourly' (1h 주기, 7d 초과 raw 삭제 — D-02)
--   (H) testuser1 J2208A 디바이스 시드 (MAC 21:02:02:06:01:69)
--
-- 의존: 001_extensions.sql 가 pg_cron 활성화, 002_tables.sql 가 public.devices 생성.
-- 적용: supabase db push  (또는)  psql $DATABASE_URL -f 010_watch_pipeline.sql
-- ============================================

-- ============================================
-- (A) ALTER public.devices  (D-01)
-- ============================================
ALTER TABLE public.devices
    ADD COLUMN IF NOT EXISTS mac_address      VARCHAR(17) UNIQUE,
    ADD COLUMN IF NOT EXISTS firmware_version VARCHAR(20),
    ADD COLUMN IF NOT EXISTS last_comm_at     TIMESTAMPTZ;

-- ============================================
-- (B) public.raw_events  (D-01·D-02)
--   - 7일 TTL via pg_cron job 아래 (G)
--   - 1초 dedup via UNIQUE (device_id, ts_truncated_to_second, raw_hash)
--     생성 컬럼은 STORED (UNIQUE constraint 사용 가능)
--   - cmd 필드는 CHECK constraint 없이 자유. 이유: D-14 silent-drop 정책에 따라
--     cmd=0x28 (HRV/혈압 응답) 행은 j2208a/supabase_writer.py 가 INSERT 자체를
--     건너뛴다. 스키마 차단이 아닌 애플리케이션 레벨 정책이므로 SQL 차원에서는
--     cmd 값에 제약을 두지 않는다.
-- ============================================
CREATE TABLE IF NOT EXISTS public.raw_events (
    raw_id      BIGSERIAL PRIMARY KEY,
    device_id   INTEGER NOT NULL REFERENCES public.devices(device_id) ON DELETE CASCADE,
    ts          TIMESTAMPTZ NOT NULL DEFAULT now(),
    cmd         SMALLINT NOT NULL,
    raw_hex     TEXT NOT NULL,
    parsed      JSONB,
    ts_truncated_to_second TIMESTAMPTZ
        GENERATED ALWAYS AS (date_trunc('second', ts)) STORED,
    raw_hash    TEXT
        GENERATED ALWAYS AS (md5(raw_hex)) STORED,
    UNIQUE (device_id, ts_truncated_to_second, raw_hash)
);
CREATE INDEX IF NOT EXISTS idx_raw_events_device_ts
    ON public.raw_events (device_id, ts DESC);

-- ============================================
-- (C) public.wear_state_events  (D-01·D-05)
--   - 5 wear-state 값: OFF / WARMUP / TRANSIENT / WORN / ABNORMAL
--   - state machine 전이 시점에만 1행 적재
-- ============================================
CREATE TABLE IF NOT EXISTS public.wear_state_events (
    event_id   BIGSERIAL PRIMARY KEY,
    device_id  INTEGER NOT NULL REFERENCES public.devices(device_id) ON DELETE CASCADE,
    ts         TIMESTAMPTZ NOT NULL DEFAULT now(),
    from_state TEXT NOT NULL,
    to_state   TEXT NOT NULL,
    reason     JSONB,
    CHECK (from_state IN ('OFF','WARMUP','TRANSIENT','WORN','ABNORMAL')),
    CHECK (to_state IN ('OFF','WARMUP','TRANSIENT','WORN','ABNORMAL'))
);
CREATE INDEX IF NOT EXISTS idx_wear_state_events_device_ts
    ON public.wear_state_events (device_id, ts DESC);

-- ============================================
-- (D) public.minute_summary  (D-01·D-08·D-17)
--   - PK = (device_id, minute_ts) → 1인 × 1분 × 1행 enforce
--   - good_ratio < 0.30 (D-17 임계 변경) 인 1분 윈도우는 결측 표기 (집계값 NULL)
-- ============================================
CREATE TABLE IF NOT EXISTS public.minute_summary (
    device_id      INTEGER NOT NULL REFERENCES public.devices(device_id) ON DELETE CASCADE,
    minute_ts      TIMESTAMPTZ NOT NULL,
    hr_median      INTEGER,
    temp_median    NUMERIC(4,1),
    temp_iqr       NUMERIC(4,1),
    steps_delta    INTEGER,
    dominant_state TEXT,
    good_ratio     NUMERIC(3,2),
    PRIMARY KEY (device_id, minute_ts),
    CHECK (dominant_state IS NULL OR dominant_state IN ('OFF','WARMUP','TRANSIENT','WORN','ABNORMAL'))
);

-- ============================================
-- (E) public.safety_alerts  (D-01·D-09)
--   - alert_type: TACHY (빈맥) / REMOVED (탈착) / COMMS_LOST (통신두절)
--   - severity:   CAUTION / WARNING / DANGER
--   - 알림 전이 원칙 (PROJECT.md): 정상↔주의↔경보 전이 시점에만 1회 적재
-- ============================================
CREATE TABLE IF NOT EXISTS public.safety_alerts (
    alert_id    BIGSERIAL PRIMARY KEY,
    device_id   INTEGER NOT NULL REFERENCES public.devices(device_id) ON DELETE CASCADE,
    alert_type  TEXT NOT NULL,
    severity    TEXT NOT NULL,
    raised_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    ack_at      TIMESTAMPTZ,
    reason      JSONB,
    CHECK (alert_type IN ('TACHY','REMOVED','COMMS_LOST')),
    CHECK (severity IN ('CAUTION','WARNING','DANGER'))
);
CREATE INDEX IF NOT EXISTS idx_safety_alerts_device_raised
    ON public.safety_alerts (device_id, raised_at DESC);

-- ============================================
-- (F) RLS ENABLE — 4 신규 테이블 (D-03)
--   정책 미등록 → service_role 전용 (003_rls_policies.sql 의 시스템 테이블 패턴 동일).
--   v1.0 BLE 클라이언트만 INSERT, 대시보드 (Phase 6 DEMO-03) 도 service_role/Edge Function 경유.
-- ============================================
ALTER TABLE public.raw_events         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wear_state_events  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.minute_summary     ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.safety_alerts      ENABLE ROW LEVEL SECURITY;

-- ============================================
-- (G) pg_cron cleanup job  (D-02)
--   1시간 주기로 7일 초과 raw_events 삭제. 001_extensions.sql 에서 pg_cron 활성화됨.
--   동일 이름 job 이 이미 있으면 cron.unschedule 로 먼저 제거 (재실행 안전).
-- ============================================
DO $$
BEGIN
    PERFORM cron.unschedule('cleanup_raw_events_hourly');
EXCEPTION WHEN OTHERS THEN
    -- 처음 실행 시 unschedule 가 not-found 로 실패하는 것이 정상 → 무시.
    NULL;
END $$;

SELECT cron.schedule(
    'cleanup_raw_events_hourly',
    '0 * * * *',
    $$DELETE FROM public.raw_events WHERE ts < now() - INTERVAL '7 days'$$
);

-- ============================================
-- (H) testuser1 J2208A 디바이스 시드 (D-13)
--   24h 운용 검증 대상. serial_number = 'J2208A-' + MAC. mac_address = MAC 그대로.
--   재실행 안전: ON CONFLICT (serial_number) DO UPDATE 로 idempotent.
-- ============================================
INSERT INTO public.devices (device_type, serial_number, mac_address, firmware_version, user_id)
VALUES ('WATCH', 'J2208A-21:02:02:06:01:69', '21:02:02:06:01:69', '0.6.3.9', 'testuser1')
ON CONFLICT (serial_number) DO UPDATE
    SET mac_address      = EXCLUDED.mac_address,
        firmware_version = EXCLUDED.firmware_version,
        user_id          = EXCLUDED.user_id;

-- ============================================
-- 적용 명령
--   supabase db push                                    (권장)
--   psql "$DATABASE_URL" -f 010_watch_pipeline.sql      (대안)
-- ============================================
