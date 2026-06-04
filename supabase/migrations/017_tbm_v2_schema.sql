-- Phase 12 - TBM v2 schema
--
-- Destructive migration: replaces Phase 9 TBM tables with the KOSHA-guide
-- aligned v2 shape. Run only after exporting existing tbm_* rows if they
-- matter in the target environment.

DROP TABLE IF EXISTS public.tbm_participants CASCADE;
DROP TABLE IF EXISTS public.tbm_checklists CASCADE;
DROP TABLE IF EXISTS public.tbm_sessions CASCADE;
DROP TABLE IF EXISTS public.tbm_templates CASCADE;

CREATE TABLE public.tbm_sessions (
    session_id        BIGSERIAL PRIMARY KEY,
    group_id          INTEGER NOT NULL REFERENCES public.groups(group_id),
    session_date      DATE NOT NULL DEFAULT CURRENT_DATE,
    work_scope        VARCHAR(80) NOT NULL,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at          TIMESTAMPTZ,
    expected_end_at   TIMESTAMPTZ NOT NULL,
    leader_user_id    VARCHAR(50) NOT NULL,
    work_type         VARCHAR(40) NOT NULL,
    location          VARCHAR(255),
    notes             TEXT,
    missed_alert_at   TIMESTAMPTZ,
    hazards_snapshot  JSONB NOT NULL DEFAULT '[]'::jsonb,
    controls_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
    key_hazard_id     VARCHAR(40),
    feedback_notes    TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT tbm_sessions_group_date_scope_uq UNIQUE (group_id, session_date, work_scope)
);

CREATE TABLE public.tbm_templates (
    template_id     SERIAL PRIMARY KEY,
    work_type       VARCHAR(40) NOT NULL UNIQUE,
    title           TEXT NOT NULL,
    description     TEXT,
    hazards         JSONB NOT NULL DEFAULT '[]'::jsonb,
    controls        JSONB NOT NULL DEFAULT '[]'::jsonb,
    key_actions     JSONB NOT NULL DEFAULT '[]'::jsonb,
    checks          JSONB NOT NULL DEFAULT '[]'::jsonb,
    target_detector VARCHAR(20),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    is_custom       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.tbm_checklists (
    checklist_id BIGSERIAL PRIMARY KEY,
    session_id   BIGINT NOT NULL REFERENCES public.tbm_sessions(session_id) ON DELETE CASCADE,
    item_idx     INTEGER NOT NULL,
    item_text    TEXT NOT NULL,
    is_checked   BOOLEAN NOT NULL DEFAULT FALSE,
    note         TEXT,
    checked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT tbm_checklists_session_item_uq UNIQUE (session_id, item_idx)
);

CREATE TABLE public.tbm_participants (
    participant_id BIGSERIAL PRIMARY KEY,
    session_id     BIGINT NOT NULL REFERENCES public.tbm_sessions(session_id) ON DELETE CASCADE,
    user_id        TEXT NOT NULL,
    signed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    signature_url  TEXT,
    method         TEXT NOT NULL DEFAULT 'signature',
    CONSTRAINT tbm_participants_session_user_uq UNIQUE (session_id, user_id),
    CONSTRAINT tbm_participants_method_chk CHECK (method IN ('signature', 'nfc', 'qr', 'manual'))
);

CREATE INDEX idx_tbm_sessions_group_date ON public.tbm_sessions(group_id, session_date);
CREATE INDEX idx_tbm_sessions_expected_open ON public.tbm_sessions(expected_end_at)
    WHERE ended_at IS NULL AND missed_alert_at IS NULL;
CREATE INDEX idx_tbm_checklists_session_idx ON public.tbm_checklists(session_id, item_idx);
CREATE INDEX idx_tbm_participants_session ON public.tbm_participants(session_id);
CREATE INDEX idx_tbm_templates_active ON public.tbm_templates(is_active) WHERE is_active = TRUE;

ALTER TABLE public.tbm_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tbm_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tbm_checklists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tbm_participants ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    CREATE POLICY "tbm_sessions_select_v2_poc" ON public.tbm_sessions
        FOR SELECT TO anon, authenticated USING (true);
    CREATE POLICY "tbm_templates_select_v2_poc" ON public.tbm_templates
        FOR SELECT TO anon, authenticated USING (true);
    CREATE POLICY "tbm_checklists_select_v2_poc" ON public.tbm_checklists
        FOR SELECT TO anon, authenticated USING (true);
    CREATE POLICY "tbm_participants_select_v2_poc" ON public.tbm_participants
        FOR SELECT TO anon, authenticated USING (true);

    -- Direct client checklist updates are retained from the Phase 9 TBM UI.
    CREATE POLICY "tbm_checklists_update_v2_poc" ON public.tbm_checklists
        FOR UPDATE TO anon, authenticated USING (true) WITH CHECK (true);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE
        public.tbm_sessions,
        public.tbm_templates,
        public.tbm_checklists,
        public.tbm_participants;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- KOSHA 가이드 한국어 본문 (도금 도메인). 2026-05-26 영어 → 한국어 교체.
INSERT INTO public.tbm_templates
    (work_type, title, description, hazards, controls, key_actions, checks, target_detector, is_active, is_custom)
VALUES
(
    'forklift',
    '지게차 작업',
    '도금 라인 자재·완제품 운반 시 사용. 운전자격·출입통제·안전벨트 3대 핵심조치.',
    '[
      {"id":"h1","text":"보행자와 충돌 (좁은 통로·시야 사각)"},
      {"id":"h2","text":"적재물 낙하 (포크 과적·불균형 적재)"},
      {"id":"h3","text":"전도 (속도 과다·급정거·경사로)"},
      {"id":"h4","text":"협착 (포크·마스트·차체와의 끼임)"}
    ]'::jsonb,
    '[
      {"id":"c1","hazard_id":"h1","level":"substitute","text":"좁은 통로에 지게차 진입 금지 (대체 통로 사용)"},
      {"id":"c2","hazard_id":"h1","level":"control","text":"보행자 출입통제 라인 및 유도자 배치"},
      {"id":"c3","hazard_id":"h2","level":"control","text":"포크 적재 한도 표시 + 운전자 적재 전 확인"},
      {"id":"c4","hazard_id":"h3","level":"control","text":"제한속도 5km/h 이하 + 급정거 금지"},
      {"id":"c5","hazard_id":"h3","level":"control","text":"안전벨트 착용 확인 (전도 시 차체 이탈 방지)"},
      {"id":"c6","hazard_id":"h4","level":"control","text":"포크 작업반경 1m 내 출입금지선 표시"}
    ]'::jsonb,
    '[
      {"id":"a1","text":"운전자격 확인 (지게차 면허 + 사업장 내 운전 허가)"},
      {"id":"a2","text":"출입통제 라인 + 유도자 배치 (보행자 분리)"},
      {"id":"a3","text":"안전벨트 착용 확인 (전도 시 차체 이탈 방지)"}
    ]'::jsonb,
    '[
      "운전자격 확인 (면허 + 사내 허가증)",
      "지게차 작업계획서 비치",
      "안전수칙 미준수 시 작업중지 권한 명시",
      "후방경광등 / 후진경보음 동작 확인",
      "포크·체인·마스트 외관 점검",
      "안전벨트 동작 확인",
      "타이어 마모·공기압 점검",
      "작업장 통로 폭 확보 + 노면 평탄",
      "출입통제 라인·표지 설치",
      "유도자 1명 이상 배치 (사각지대 보행자 안내)",
      "적재물 결속·균형 확인",
      "포크 1~2단 (지면 가까이) 이동",
      "운전자 안전벨트 착용",
      "급정거·급회전 금지"
    ]'::jsonb,
    'forklift',
    TRUE,
    FALSE
),
(
    'chemical',
    '화학물질 취급 (산세·도금액)',
    '황산·염산·질산 등 산세조, 니켈·크롬·시안화물 도금조 취급 시 노출·튐·증기 흡입 위험 관리.',
    '[
      {"id":"h1","text":"산·알칼리 튐 → 피부·눈 손상"},
      {"id":"h2","text":"유증기 흡입 → 호흡기·점막 손상"},
      {"id":"h3","text":"시안화물 누출 → 청산 가스 생성 (산 접촉 시)"},
      {"id":"h4","text":"폐액 혼합 → 발열·돌비"}
    ]'::jsonb,
    '[
      {"id":"c1","hazard_id":"h1","level":"eliminate","text":"무인 자동공급 라인으로 대체 (인력 투입 제거)"},
      {"id":"c2","hazard_id":"h1","level":"substitute","text":"산·도금조 분리 격벽 + 펌프 자동공급"},
      {"id":"c3","hazard_id":"h1","level":"control","text":"내산 장갑·고글·앞치마·안면보호구 착용"},
      {"id":"c4","hazard_id":"h2","level":"control","text":"국소배기장치 후드 위치·풍속 점검 (0.4m/s 이상)"},
      {"id":"c5","hazard_id":"h3","level":"control","text":"시안조와 산조 동일 동선 금지 (혼입 사고 차단)"},
      {"id":"c6","hazard_id":"h4","level":"control","text":"폐액 분류 보관·혼합 작업 금지 (지정 처리업체 위탁)"}
    ]'::jsonb,
    '[
      {"id":"a1","text":"MSDS 비치 + 작업자 교육 이수 확인"},
      {"id":"a2","text":"보호구 완전착용 (내산 장갑·고글·앞치마·호흡보호구)"},
      {"id":"a3","text":"비상샤워·세안기 가동 확인 + 5m 이내 위치"}
    ]'::jsonb,
    '[
      "MSDS 게시 + 작업자 숙지",
      "보호구 (내산 장갑·고글·앞치마·호흡보호구) 착용",
      "비상샤워·세안기 동작 확인 + 5m 이내 위치",
      "국소배기장치 가동 (후드 풍속 0.4m/s 이상)",
      "산조·도금조·시안조 동선 분리",
      "약품 농도·온도 표시판 부착",
      "폐액 분리 보관 + 라벨링",
      "환기 상태 (체류 가스 측정 50ppm 이하)",
      "비상연락망 + 응급조치 절차 게시",
      "출입통제 + 외부인 진입 차단"
    ]'::jsonb,
    'fire',
    TRUE,
    FALSE
),
(
    'hot_work',
    '고온·열처리 작업',
    '전기도금 라인 발열·열처리로·건조로 운영 시 화상·화재·증기 위험 관리.',
    '[
      {"id":"h1","text":"고온 표면 접촉 → 화상"},
      {"id":"h2","text":"가연성 도금액 인접 → 발화·화재"},
      {"id":"h3","text":"고온 증기·미스트 흡입 → 호흡기 손상"},
      {"id":"h4","text":"열처리로 폭발 (가스 누출·과압)"}
    ]'::jsonb,
    '[
      {"id":"c1","hazard_id":"h4","level":"eliminate","text":"열처리로 자동 인터록 (도어 열림 시 가열 차단)"},
      {"id":"c2","hazard_id":"h1","level":"substitute","text":"고온부 단열재 피복 + 안전망 설치"},
      {"id":"c3","hazard_id":"h1","level":"control","text":"내열 장갑·앞치마·안면보호구 착용"},
      {"id":"c4","hazard_id":"h2","level":"control","text":"열처리로 주변 2m 가연물 비치 금지"},
      {"id":"c5","hazard_id":"h3","level":"control","text":"강제배기 + 외부 토출 (실내 농도 25ppm 이하)"},
      {"id":"c6","hazard_id":"h4","level":"control","text":"가스 누출 감지기 + 자동 차단밸브"}
    ]'::jsonb,
    '[
      {"id":"a1","text":"가연물 격리 + 소화기 5m 이내 비치"},
      {"id":"a2","text":"내열 보호구 완전착용 (장갑·앞치마·안면보호구)"},
      {"id":"a3","text":"가스 누출 감지기 정상 + 비상정지 버튼 위치 숙지"}
    ]'::jsonb,
    '[
      "열처리로·건조로 외관·인터록 점검",
      "고온부 단열 피복 + 안전망 상태",
      "내열 장갑·앞치마·안면보호구 착용",
      "주변 2m 가연물 제거",
      "소화기·소화전 5m 이내 비치 + 점검필 확인",
      "가스 누출 감지기 동작 (가연성 25%LEL 이하)",
      "강제배기·국소배기 가동",
      "비상정지 버튼 위치·동작 확인",
      "온도 표시판 + 작업 한계 온도 게시",
      "통신수단 + 비상연락망"
    ]'::jsonb,
    'fire',
    TRUE,
    FALSE
);

CREATE OR REPLACE FUNCTION public.tbm_missed_attendance_check()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions, net
AS $$
DECLARE
    s RECORD;
    edge_url TEXT;
    sr_key TEXT;
BEGIN
    SELECT decrypted_secret INTO edge_url
    FROM vault.decrypted_secrets
    WHERE name = 'edge_function_base_url'
    LIMIT 1;

    SELECT decrypted_secret INTO sr_key
    FROM vault.decrypted_secrets
    WHERE name = 'service_role_key'
    LIMIT 1;

    IF edge_url IS NULL OR sr_key IS NULL THEN
        RAISE WARNING 'tbm_missed_attendance_check skipped: missing Vault edge URL or service role key';
        RETURN;
    END IF;

    FOR s IN
        UPDATE public.tbm_sessions
        SET missed_alert_at = NOW()
        WHERE ended_at IS NULL
          AND missed_alert_at IS NULL
          AND expected_end_at < NOW() - INTERVAL '30 minutes'
        RETURNING session_id, group_id, leader_user_id, work_scope
    LOOP
        PERFORM net.http_post(
            url := edge_url || '/functions/v1/notifications',
            headers := jsonb_build_object(
                'Content-Type', 'application/json',
                'Authorization', 'Bearer ' || sr_key
            ),
            body := jsonb_build_object(
                'action', 'tbm-missed',
                'session_id', s.session_id,
                'group_id', s.group_id,
                'leader_user_id', s.leader_user_id,
                'work_scope', s.work_scope
            )
        );
    END LOOP;
END;
$$;

DO $$
BEGIN
    PERFORM cron.unschedule('tbm_missed_attendance_minute');
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

SELECT cron.schedule(
    'tbm_missed_attendance_minute',
    '* * * * *',
    $$SELECT public.tbm_missed_attendance_check();$$
);
