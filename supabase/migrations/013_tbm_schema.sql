-- ============================================
-- 013: TBM Worker Guide Schema (Phase 9 TBM-01)
-- Phase 9 — per CONTEXT.md D-01 / D-02 / D-03 / D-05 + RESEARCH C2 (Realtime 4 ADD) / C3 (Storage Option A)
--
-- Adds:
--   (A) 4 신규 테이블 — tbm_sessions / tbm_templates / tbm_checklists / tbm_participants
--       · tbm_sessions UNIQUE (group_id, session_date) — D-02 (1 group × 1 day × 1 session v1.0)
--       · tbm_participants UNIQUE (session_id, user_id) — idempotent 체크인
--       · tbm_participants.method CHECK IN ('signature','nfc','qr','manual') — D-03
--       · tbm_sessions.missed_alert_at TIMESTAMPTZ — D-05 알림 전이 1회 dedup (Phase 4 D-09 패턴)
--   (B) 인덱스 — group_date / session_id / item_idx
--   (C) RLS — 4 테이블 ENABLE + SELECT USING (true) v1.0 PoC (Phase 7 011 패턴 미러)
--       · write 정책 미등록 = service_role 만 (D-08 Edge Function 경유)
--   (D) Realtime publication ADD TABLE 4 (research C2 amendment — 4 테이블 모두)
--   (E) Storage 버킷 'tbm-signatures' — public=false + Option A anon INSERT + key prefix 가드 (research C3)
--       · 모든 read = service_role signed URL 60s (SELECT 정책 미등록 = default deny)
--   (F) 5 templates 시드 — work_type IN ('fire','electric','height','heavy','general') (D-01 시드)
--   (G) public.tbm_missed_attendance_check() — SECURITY DEFINER plpgsql 함수 (D-05)
--       · Vault sr_key + edge_function_base_url SELECT + NULL 가드 graceful skip (Phase 8 012 패턴)
--       · expected_end_at + 30분 임계 + missed_alert_at IS NULL AND ended_at IS NULL
--       · net.http_post 으로 notifications Edge Function 'tbm-missed' 호출 (plan 09-02 신규)
--   (H) pg_cron 'tbm_missed_attendance_minute' 1분 주기 등록 (010/012 idempotent 패턴)
--
-- 의존: 001_extensions.sql 가 pg_cron 활성화, 012 가 pg_net 활성화 + edge_function_base_url Vault 시드,
--       002_tables.sql 가 public.groups + profiles 생성, 004_storage.sql 가 storage 스키마 정착.
-- 적용: supabase db push --linked --yes
--
-- threat mitigations (09-01-PLAN.md threat_model 참조):
--   T-9-01 (signature PII): bucket public=false + anon INSERT path 가드 + SELECT 정책 미등록 (default deny)
--   T-9-02 (UNIQUE bypass): DB-level UNIQUE (group_id, session_date) + Edge Function 23505 catch (plan 09-02)
--   T-9-05 (Vault 미시드 DoS): IF sr_key/base_url IS NULL → RAISE WARNING + RETURN graceful skip
--   T-9-07 (Vault git 노출): 마이그 본문에 service_role JWT 0건 — vault.decrypted_secrets SELECT 만 사용
--   T-9-08 (anon 임의 INSERT): RLS write 정책 미등록 = default deny (service_role Edge Function 전용)
-- ============================================

-- ============================================
-- (A) 4 신규 테이블 (CONTEXT.md D-01 그대로)
-- ============================================

-- (1) tbm_sessions: 일자 × 작업장 × 리더 × 작업유형
CREATE TABLE IF NOT EXISTS public.tbm_sessions (
    session_id        BIGSERIAL PRIMARY KEY,
    group_id          INTEGER NOT NULL REFERENCES public.groups(group_id),
    session_date      DATE NOT NULL DEFAULT CURRENT_DATE,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at          TIMESTAMPTZ,
    expected_end_at   TIMESTAMPTZ NOT NULL,             -- 관리자 지정 시각 (D-05)
    leader_user_id    VARCHAR(50) NOT NULL,              -- profiles.user_id (manager 권한)
    work_type         VARCHAR(40) NOT NULL,              -- tbm_templates.work_type (의미상 FK)
    location          VARCHAR(255),                       -- 작업장 위치 (옵션, group 별 default)
    notes             TEXT,
    missed_alert_at   TIMESTAMPTZ,                       -- 미참여 알림 발사 시각 (D-05 1회 dedup)
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT tbm_sessions_group_date_uq UNIQUE (group_id, session_date)
);

-- (2) tbm_templates: 작업유형별 체크리스트 템플릿 (JSONB array)
CREATE TABLE IF NOT EXISTS public.tbm_templates (
    template_id   SERIAL PRIMARY KEY,
    work_type     VARCHAR(40) NOT NULL UNIQUE,
    title         VARCHAR(100) NOT NULL,
    checklist     JSONB NOT NULL,                        -- ["인화성 물질 격리 확인", ...]
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- (3) tbm_checklists: 세션별 체크 항목 + 체크 상태 + 근거 (snapshot — 템플릿 변경에도 이력 보존)
CREATE TABLE IF NOT EXISTS public.tbm_checklists (
    checklist_id  BIGSERIAL PRIMARY KEY,
    session_id    BIGINT NOT NULL REFERENCES public.tbm_sessions(session_id) ON DELETE CASCADE,
    item_idx      INTEGER NOT NULL,                      -- template.checklist 의 array index
    item_text     TEXT NOT NULL,                          -- snapshot at session start
    is_checked    BOOLEAN NOT NULL DEFAULT false,
    note          TEXT,
    checked_at    TIMESTAMPTZ,
    CONSTRAINT tbm_checklists_session_item_uq UNIQUE (session_id, item_idx)
);

-- (4) tbm_participants: 참여 작업자 + 서명 + 체크인 시각
CREATE TABLE IF NOT EXISTS public.tbm_participants (
    participant_id  BIGSERIAL PRIMARY KEY,
    session_id      BIGINT NOT NULL REFERENCES public.tbm_sessions(session_id) ON DELETE CASCADE,
    user_id         VARCHAR(50) NOT NULL,                -- profiles.user_id
    signed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    signature_url   TEXT,                                 -- Storage 키 (D-03 — Compose Canvas → PNG)
    method          VARCHAR(20) NOT NULL DEFAULT 'signature'
        CHECK (method IN ('signature','nfc','qr','manual')),  -- v1.0 'signature' only
    CONSTRAINT tbm_participants_session_user_uq UNIQUE (session_id, user_id)
);

-- ============================================
-- (B) 인덱스 (CONTEXT.md D-01)
-- ============================================
CREATE INDEX IF NOT EXISTS idx_tbm_sessions_group_date
    ON public.tbm_sessions (group_id, session_date DESC);
CREATE INDEX IF NOT EXISTS idx_tbm_participants_session
    ON public.tbm_participants (session_id);
CREATE INDEX IF NOT EXISTS idx_tbm_checklists_session
    ON public.tbm_checklists (session_id, item_idx);

-- ============================================
-- (C) RLS — v1.0 PoC 패턴 (Phase 7 011 미러)
--   ENABLE 4 + SELECT USING (true) v1.0 PoC + write 정책 미등록 = service_role 만 (D-08)
-- ============================================
ALTER TABLE public.tbm_sessions     ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tbm_templates    ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tbm_checklists   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tbm_participants ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    BEGIN
        CREATE POLICY "tbm_sessions_select_v1_poc" ON public.tbm_sessions
            FOR SELECT TO anon, authenticated USING (true);
    EXCEPTION WHEN duplicate_object THEN NULL; END;

    BEGIN
        CREATE POLICY "tbm_templates_select_v1_poc" ON public.tbm_templates
            FOR SELECT TO anon, authenticated USING (true);
    EXCEPTION WHEN duplicate_object THEN NULL; END;

    BEGIN
        CREATE POLICY "tbm_checklists_select_v1_poc" ON public.tbm_checklists
            FOR SELECT TO anon, authenticated USING (true);
    EXCEPTION WHEN duplicate_object THEN NULL; END;

    BEGIN
        CREATE POLICY "tbm_participants_select_v1_poc" ON public.tbm_participants
            FOR SELECT TO anon, authenticated USING (true);
    EXCEPTION WHEN duplicate_object THEN NULL; END;
END $$;

-- ============================================
-- (D) Realtime publication ADD TABLE 4 (research C2 — 4 테이블 모두)
--   Phase 7 011 패턴 1:1 미러. 이미 등록된 경우 NOTICE 후 무시 (재실행 안전).
-- ============================================
DO $$
BEGIN
    BEGIN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.tbm_sessions;
    EXCEPTION WHEN duplicate_object THEN NULL; END;

    BEGIN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.tbm_templates;
    EXCEPTION WHEN duplicate_object THEN NULL; END;

    BEGIN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.tbm_checklists;
    EXCEPTION WHEN duplicate_object THEN NULL; END;

    BEGIN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.tbm_participants;
    EXCEPTION WHEN duplicate_object THEN NULL; END;
END $$;

-- ============================================
-- (E) Storage 버킷 'tbm-signatures' — v1.0 Option A (research C3)
--   public=false + anon INSERT WITH CHECK (bucket_id + key prefix 가드 + .png suffix)
--   SELECT 정책 미등록 = default deny — 모든 read 는 service_role signed URL 60s (plan 09-02 발급)
--   PII 보호: signature PNG 는 Storage object 직접 GET 불가, service_role 만 발급 가능
-- ============================================
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'tbm-signatures',
    'tbm-signatures',
    false,
    524288,                                              -- 512KB (서명 PNG 충분 + DoS 가드)
    ARRAY['image/png']
)
ON CONFLICT (id) DO NOTHING;

DO $$
BEGIN
    BEGIN
        CREATE POLICY "tbm_signatures_insert_anon"
            ON storage.objects FOR INSERT
            TO anon, authenticated
            WITH CHECK (
                bucket_id = 'tbm-signatures'
                AND (storage.foldername(name))[1] ~ '^[0-9]+$'   -- {session_id} = 숫자만
                AND lower(right(name, 4)) = '.png'                 -- .png suffix 강제
            );
    EXCEPTION WHEN duplicate_object THEN NULL; END;
END $$;
-- SELECT 정책 미등록 = default deny — anon GET 차단, service_role signed URL 60s only.

-- ============================================
-- (F) 5 templates 시드 (CONTEXT D-01 — 화재/전기/고소/중량물/일반)
--   ROADMAP §"화재 위험·전기·고소·중량물" + 일반 = 5종 work_type
--   ON CONFLICT (work_type) DO NOTHING — 재실행 안전 (UNIQUE work_type 기준)
-- ============================================
INSERT INTO public.tbm_templates (work_type, title, checklist) VALUES
    ('fire',     '화재 위험 작업',
     '["인화성 물질 격리 확인","소화기 위치 확인","비상 대피로 확인","화재 감지기 동작 확인","불티 비산 방지포 설치"]'::jsonb),
    ('electric', '전기 작업',
     '["전원 차단 확인 (LOTO)","검전기 확인","절연 장갑/매트 사용","접지 상태 확인","아크 차단기 점검"]'::jsonb),
    ('height',   '고소 작업',
     '["안전대 착용 확인","안전모 턱끈 체결","발판/사다리 견고성","낙하물 방지망 설치","2m 이상 작업 시 추락 방지 조치"]'::jsonb),
    ('heavy',    '중량물 취급',
     '["지게차 운행 경로 확인","호이스트/슬링 점검","적재 안정성 확인","작업 반경 출입 통제","협착 위험 부위 확인"]'::jsonb),
    ('general',  '일반 작업',
     '["안전모 착용","안전화 착용","보안경 착용 (필요 시)","작업장 정리정돈","비상 연락망 확인"]'::jsonb)
ON CONFLICT (work_type) DO NOTHING;

-- ============================================
-- (G) public.tbm_missed_attendance_check()  (CONTEXT D-05 + Phase 8 012 미러)
--   SECURITY DEFINER (Pitfall 7) + SET search_path = public, extensions, net
--   Vault sr_key/base_url NULL 가드 — graceful skip (Phase 8 04 검증 패턴)
--   알림 전이 원칙 (Phase 4 D-09): missed_alert_at IS NULL 인 세션 1회만 발사
--   Plan 09-02 의 notifications/index.ts case 'tbm-missed' 가 받을 payload 계약 일관
-- ============================================
CREATE OR REPLACE FUNCTION public.tbm_missed_attendance_check() RETURNS void
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public, extensions, net
AS $$
DECLARE
    r        RECORD;
    sr_key   TEXT;
    base_url TEXT;
BEGIN
    -- Vault 에서 secrets SELECT (Phase 8 012 패턴 1:1 미러)
    SELECT decrypted_secret INTO sr_key
        FROM vault.decrypted_secrets WHERE name = 'service_role_key' LIMIT 1;
    SELECT decrypted_secret INTO base_url
        FROM vault.decrypted_secrets WHERE name = 'edge_function_base_url' LIMIT 1;

    IF sr_key IS NULL OR base_url IS NULL THEN
        -- Vault 시드 미완료 시 silent skip (대시보드 시드 후 다음 cron tick 부터 동작).
        -- T-9-05 mitigation: graceful skip — cron 자체는 1분마다 정상 종료.
        RAISE WARNING 'tbm_missed_attendance_check: vault secret missing (sr_key_present=%, base_url_present=%) — skip',
            (sr_key IS NOT NULL), (base_url IS NOT NULL);
        RETURN;
    END IF;

    -- ─── 미참여 알림 발사 (D-05 expected_end_at + 30분 임계, 1회 dedup) ───
    FOR r IN
        SELECT session_id, group_id, expected_end_at, leader_user_id
        FROM public.tbm_sessions
        WHERE expected_end_at + INTERVAL '30 minutes' < now()
          AND missed_alert_at IS NULL
          AND ended_at IS NULL                    -- 종료된 세션은 skip (D-05)
    LOOP
        -- 상태 전이 먼저 (race 방지 — http_post 실패해도 다음 cycle 에 재시도 안 됨).
        -- D-09 의 "전이 시점에만 1회" 보존 — missed_alert_at 채워지면 다음 cycle 에 skip.
        UPDATE public.tbm_sessions
            SET missed_alert_at = now()
            WHERE session_id = r.session_id;

        -- pg_net 비동기 fire-and-forget. response 무시.
        -- Plan 09-02 의 notifications/index.ts case 'tbm-missed' 가 처리.
        PERFORM net.http_post(
            url     := base_url || '/notifications',
            headers := jsonb_build_object(
                'Authorization', 'Bearer ' || sr_key,
                'Content-Type',  'application/json'
            ),
            body    := jsonb_build_object(
                'action',         'tbm-missed',
                'session_id',     r.session_id,
                'group_id',       r.group_id,
                'leader_user_id', r.leader_user_id
            )
        );
    END LOOP;
END;
$$;

COMMENT ON FUNCTION public.tbm_missed_attendance_check() IS
    'Phase 9 TBM-03: expected_end_at + 30분 경과 + 미종료 세션의 미참여 알림 1회 발사. '
    'Phase 4 D-09 알림 전이 원칙 적용 (missed_alert_at 채워지면 dedup). '
    'Vault sr_key/base_url 미시드 시 graceful skip (T-9-05).';

-- ============================================
-- (H) pg_cron 1분 주기 등록 (010/012 idempotent 패턴)
--   동일 이름 job 이 이미 있으면 unschedule 먼저 (재실행 안전).
-- ============================================
DO $$
BEGIN
    PERFORM cron.unschedule('tbm_missed_attendance_minute');
EXCEPTION WHEN OTHERS THEN
    -- 처음 실행 시 unschedule 가 not-found 로 실패하는 것이 정상 → 무시.
    NULL;
END $$;

SELECT cron.schedule(
    'tbm_missed_attendance_minute',
    '* * * * *',
    $$SELECT public.tbm_missed_attendance_check();$$
);

-- ============================================
-- Migration 013 complete.
-- ============================================
