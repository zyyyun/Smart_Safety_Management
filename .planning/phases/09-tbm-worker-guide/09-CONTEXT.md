# Phase 9: TBM 현장 작업자 가이드 - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning
**Mode:** `--auto` (autonomous, no clarifying questions per user directive)

<domain>
## Phase Boundary

작업 시작 전 TBM (Tool Box Meeting) 세션에 현장 작업자가 직접 참여하는 가이드 — (1)
관리자가 오늘 TBM 세션 생성 + 작업 유형(work_type) 선택 + 템플릿 기반 체크리스트
표시, (2) 작업자가 자신의 폰에서 체크리스트 확인 + 수기 서명(Compose Canvas)으로
체크인, (3) 관리자 화면에 작업자별 일자별 TBM 참여 여부 표시, (4) 세션의 관리자
지정 시각(`expected_end_at`) + 30분 경과 시 미참여 작업자에게 FCM 알림. 기존
`daily_safety_check` (관리자 순회 점검) 메뉴와 **별도 경로** 로 동시 운용.

**가벼운 통합 방향**: Phase 7 (워치) 와 같은 패턴 — Supabase 마이그레이션 1개(013) +
Edge Function action 1~2개(`tbm-pair`, `tbm-checkin` 정도) + Android `tbm/` 패키지
신규 (Phase 7 의 `watch/` 패키지 미러) + HomeActivity/HomeWorkerActivity 에 카드
1개씩 추가 + 신규 Activity 2~3개 (관리자 대시보드 + 작업자 가이드). 4 detector 코드
0 변경, watch/ 패키지 0 변경.

**Out of scope** (다른 phase / future):
- **명시적 출근(attendance) 체크인 시스템** — v1.0 부재. 본 phase 의 "미참여 대상"
  은 `profiles.user_role IN ('worker','general_manager')` AND `group_id = session.group_id`
  전원으로 단순 정의 (D-04 참조). 실제 출근 시스템은 v1.1+ 별도 phase.
- **다중 작업장(workplace) TBM 동시 운용** — v1.0 한정 1 group = 1 session/day (D-02).
  복수 작업장은 v1.1 (각 작업장별 세션 분리).
- **NFC / QR 체크인** — v1.0 한정 수기 서명 단일 채택 (D-03 근거). NFC 는 디바이스
  의존성, QR 은 관리자 단말 표시 + 작업자 스캔 워크플로우 부담 → v1.1+ 옵션.
- **TBM 음성 안내 / TTS 가이드** → Next-5 PTT 음성 트랙
- **다국어 체크리스트** → v1.x
- **TBM 사진 첨부 (안전 장구 착용 확인 사진)** → v1.1+
- **TBM 통계 / 월별 참여율 리포트** → Phase 6 DEMO 또는 v1.x
- **체크리스트 항목별 자동 위험도 매핑 (LP-5 룰 seed 연동)** → v1.1
- **세션 종료 후 관리자 승인 워크플로우** → v1.1 (현재는 작업자 서명 즉시 참여 완료)

</domain>

<spec_lock>
## Locked Requirements (from ROADMAP.md)

3 requirements (TBM-01·02·03), 4 Success Criteria — 본 phase 의 acceptance 는
ROADMAP.md Phase 9 섹션(line 273-292) 을 직접 참조한다.

- **TBM-01**: 4 신규 테이블 (`tbm_sessions` / `tbm_checklists` / `tbm_participants`
  / `tbm_templates`) + RLS 정책 + Realtime publication 등록.
- **TBM-02**: Android TBM 가이드 화면 — 오늘 TBM 세션 시작 → 작업 유형 선택 →
  템플릿 기반 체크리스트 → 참여 작업자 체크인 (**수기 서명 단일 채택, D-03**) →
  세션 종료 + Supabase 적재. 실기기 또는 에뮬레이터 1회 사이클 캡처.
- **TBM-03**: 관리자 화면 — 작업자별 일자별 TBM 참여 여부 표시 + 출근했으나 미참여
  작업자에게 FCM 알림 (관리자 지정 시각 + 30분 경과 시점, D-05). 1일 사이클 검증.

**SC #4 (cross-cutting)**: 기존 일일 안전 점검(`daily_safety_check`) 과 *별도* 메뉴
— 코드 경로 분리 (테이블 분리, Activity 분리, Adapter 분리, Edge Function action 분리),
작업자 권한과 관리자 권한 분리. ✓ 본 phase 의 D-01·D-06·D-08 이 자동 충족.

**마감일**: 명시 없음 (Phase 7 의 수요일 마감과 달리 무마감). ROADMAP estimate 3일.
v1.0 5월 PPT 데모 흐름에 통합 가능 (3·4·7·8 + 9 통합 시연).

</spec_lock>

<decisions>
## Implementation Decisions

### 마이그레이션 스키마 (TBM-01)
- **D-01: `supabase/migrations/013_tbm_schema.sql` — 4 신규 테이블 + RLS + Realtime publication**
  - **번호**: 012 다음 = **013** (Phase 8 cameras_health 패턴 동일, 010/011/012 idempotent 패턴 재사용).
  - **테이블 4종**:
    ```sql
    -- (1) tbm_sessions: 일자 × 작업장 × 리더 × 작업유형
    CREATE TABLE public.tbm_sessions (
      session_id        BIGSERIAL PRIMARY KEY,
      group_id          INTEGER NOT NULL REFERENCES public.groups(group_id),
      session_date      DATE NOT NULL DEFAULT CURRENT_DATE,
      started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
      ended_at          TIMESTAMPTZ,
      expected_end_at   TIMESTAMPTZ NOT NULL,   -- 관리자 지정 시각 (D-05)
      leader_user_id    VARCHAR(50) NOT NULL,    -- profiles.user_id (manager 권한)
      work_type         VARCHAR(40) NOT NULL,    -- tbm_templates.work_type FK 의미상
      location          VARCHAR(255),             -- 작업장 위치 (옵션, group 별 default)
      notes             TEXT,
      missed_alert_at   TIMESTAMPTZ,             -- 미참여 알림 발사 시각 (D-09 30분 cooldown)
      created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
      UNIQUE (group_id, session_date)            -- v1.0: 1 group × 1 day = 1 session (D-02)
    );

    -- (2) tbm_templates: 작업유형별 체크리스트 템플릿 (JSONB array)
    CREATE TABLE public.tbm_templates (
      template_id   SERIAL PRIMARY KEY,
      work_type     VARCHAR(40) NOT NULL UNIQUE,
      title         VARCHAR(100) NOT NULL,
      checklist     JSONB NOT NULL,              -- ["화재 위험 확인", "전기 차단", ...]
      created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    -- (3) tbm_checklists: 세션별 체크 항목 + 체크 상태 + 근거
    CREATE TABLE public.tbm_checklists (
      checklist_id  BIGSERIAL PRIMARY KEY,
      session_id    BIGINT NOT NULL REFERENCES public.tbm_sessions(session_id) ON DELETE CASCADE,
      item_idx      INTEGER NOT NULL,            -- template.checklist 의 array index
      item_text     TEXT NOT NULL,                -- snapshot (템플릿 변경에도 이력 보존)
      is_checked    BOOLEAN NOT NULL DEFAULT false,
      note          TEXT,
      checked_at    TIMESTAMPTZ,
      UNIQUE (session_id, item_idx)
    );

    -- (4) tbm_participants: 참여 작업자 + 서명 + 체크인 시각
    CREATE TABLE public.tbm_participants (
      participant_id  BIGSERIAL PRIMARY KEY,
      session_id      BIGINT NOT NULL REFERENCES public.tbm_sessions(session_id) ON DELETE CASCADE,
      user_id         VARCHAR(50) NOT NULL,       -- profiles.user_id
      signed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
      signature_url   TEXT,                        -- Storage 키 (D-07 — Compose Canvas → PNG 업로드)
      method          VARCHAR(20) NOT NULL DEFAULT 'signature'
        CHECK (method IN ('signature','nfc','qr','manual')),   -- v1.0 'signature' only
      UNIQUE (session_id, user_id)                 -- 중복 참여 방지
    );
    ```
  - **인덱스**:
    ```sql
    CREATE INDEX idx_tbm_sessions_group_date ON public.tbm_sessions (group_id, session_date DESC);
    CREATE INDEX idx_tbm_participants_session ON public.tbm_participants (session_id);
    CREATE INDEX idx_tbm_checklists_session ON public.tbm_checklists (session_id, item_idx);
    ```
  - **시드 데이터** (5종 작업유형, ROADMAP §"화재 위험·전기·고소·중량물" + 일반):
    ```sql
    INSERT INTO public.tbm_templates (work_type, title, checklist) VALUES
      ('fire',     '화재 위험 작업',  '["인화성 물질 격리 확인","소화기 위치 확인","비상 대피로 확인","화재 감지기 동작 확인","불티 비산 방지포 설치"]'::jsonb),
      ('electric', '전기 작업',       '["전원 차단 확인 (LOTO)","검전기 확인","절연 장갑/매트 사용","접지 상태 확인","아크 차단기 점검"]'::jsonb),
      ('height',   '고소 작업',       '["안전대 착용 확인","안전모 턱끈 체결","발판/사다리 견고성","낙하물 방지망 설치","2m 이상 작업 시 추락 방지 조치"]'::jsonb),
      ('heavy',    '중량물 취급',     '["지게차 운행 경로 확인","호이스트/슬링 점검","적재 안정성 확인","작업 반경 출입 통제","협착 위험 부위 확인"]'::jsonb),
      ('general',  '일반 작업',       '["안전모 착용","안전화 착용","보안경 착용 (필요 시)","작업장 정리정돈","비상 연락망 확인"]'::jsonb)
    ON CONFLICT (work_type) DO NOTHING;
    ```
  - **RLS**: v1.0 한정 — Phase 7 D-04b 와 동일 원칙 (Supabase Auth 부재 → service_role
    Edge Function 경유 write, anon SELECT 는 `USING (true)` 또는 narrowing 패턴).
    ```sql
    ALTER TABLE public.tbm_sessions ENABLE ROW LEVEL SECURITY;
    ALTER TABLE public.tbm_templates ENABLE ROW LEVEL SECURITY;
    ALTER TABLE public.tbm_checklists ENABLE ROW LEVEL SECURITY;
    ALTER TABLE public.tbm_participants ENABLE ROW LEVEL SECURITY;
    -- v1.0: 모든 SELECT 허용 (Phase 7 011 패턴), write 는 Edge Function 만
    CREATE POLICY "tbm_sessions_select_v1_poc" ON public.tbm_sessions
      FOR SELECT TO anon, authenticated USING (true);
    -- (templates / checklists / participants 동일 패턴)
    -- write 정책 = 등록 안 함 → service_role 만 (D-08 Edge Function 경유)
    ```
  - **Realtime publication** (amended 2026-05-18 via research C2): 011 패턴 미러 —
    **4 테이블 모두 등록** (단순성 우선, Phase 7 011 패턴 직접 미러). tbm_templates
    의 trafffic 은 작지만 시드 후 보강 시 즉시 클라이언트 갱신 가능 + 4-채널 구독이
    Realtime SDK 의 단일 channel 호출로 가능 (구조 동일):
    ```sql
    ALTER PUBLICATION supabase_realtime ADD TABLE
      public.tbm_sessions, public.tbm_templates,
      public.tbm_checklists, public.tbm_participants;
    ```
  - **격리 테스트**: `tests/sql/test_013_tbm_isolation.sql` — (a) anon UPDATE 차단,
    (b) Edge Function service_role 만 write 가능, (c) 5 templates 시드 확인,
    (d) cron 등록 확인 (D-09).
  - **근거**: Phase 4 010 + Phase 7 011 + Phase 8 012 의 패턴 그대로 미러. UNIQUE 제약
    이 멱등성 + 동시성 보호. JSONB checklist 가 v1.0 단순성 ↔ v1.1 정규화 진화 경로.

### 세션 lifecycle / 작업자 입장 (TBM-02)
- **D-02: Manager-led 세션, Worker-joins 참여 (Phase 7 패턴 일관)**
  - **v1.0 한정 1 group × 1 day × 1 session** — UNIQUE `(group_id, session_date)`
    제약 (D-01 참조). 작업장 분리 / 오전·오후 분리는 v1.1+.
  - **흐름**:
    1. **관리자**: HomeActivity 의 신규 "오늘 TBM" 카드 (D-06) 클릭 → TbmSessionActivity
       (manager) → 작업유형 선택 + 예정 종료 시각(`expected_end_at`) 입력 + 위치
       (default = group 의 workplace) → "세션 시작" → Edge Function `tbm-start` 호출
       → `tbm_sessions` row insert + `tbm_checklists` row N개 (template snapshot) insert
       + 그룹 worker 전원에게 FCM `tbm-started` 발사 (Phase 8 sendPushToUsers 패턴).
    2. **작업자**: FCM 알림 도착 → HomeWorkerActivity 진입 시 watch card 아래 "오늘
       TBM" 카드 (D-07) 가 active 상태 표시 → 클릭 → TbmWorkerActivity → 체크리스트
       표시 (read-only 또는 manager 입력 후 read-only) + Compose Canvas 서명 영역
       → "참여 확인" → Edge Function `tbm-checkin` 호출 → `tbm_participants` row insert
       + signature PNG 업로드.
    3. **관리자**: 참여자 목록이 Realtime 으로 즉시 갱신. 모두 참여 시 "세션 종료"
       버튼 → Edge Function `tbm-end` → `tbm_sessions.ended_at = now()`.
  - **체크리스트 입력 책임**: 관리자가 세션 시작 시 템플릿 그대로 사용 + 체크 표시
    (`is_checked = true`) + note 추가 가능. 작업자는 read-only (단순화). v1.1 에서
    작업자도 체크 가능 옵션 검토.
  - **근거**: 산업안전 TBM 관례 = 관리자 주재 + 작업자 참여. Phase 7 D-04b (manager
    → worker FCM trigger) 패턴 일관. UNIQUE `(group_id, session_date)` 가 idempotency.

### 체크인 방법 (TBM-02 — "NFC/QR/수기 서명 중 1")
- **D-03: 수기 서명 (Compose Canvas → PNG → Supabase Storage) 단일 채택**
  - **선택 근거** (3 옵션 비교):
    - **수기 서명 (Canvas)**: 외부 하드웨어/디바이스 의존성 0, 모든 Android 기기에서
      동작, 5월 PPT 데모에서 시연 안정, 6월 현장에서도 즉시 동작.
    - **NFC**: 일부 저가 기기 미지원, 작업자 폰에 NFC 활성화 요구, 카드/태그 발급 필요.
    - **QR**: 관리자 단말이 QR 표시 + 작업자가 카메라로 스캔 → 카메라 권한 + 양 단말
      운용 부담. 사이트 시연 환경 부재로 추가 워크플로우 검증 어려움.
  - **구현**:
    - `app/src/main/java/com/example/smart_safety_management/tbm/SignatureCanvas.kt` —
      Compose Canvas 100% (Path 누적 + drawPath). 가로 길이 ~80% 화면, 높이 200dp,
      stroke = 4dp, color = onSurface. "지우기" / "저장" 버튼.
    - 저장 시 Canvas → Bitmap → PNG → Supabase Storage 버킷 `tbm-signatures` 업로드
      (신규 버킷, public read X, service_role write only).
    - 키 컨벤션: `{session_id}/{user_id}_{timestamp}.png` (e.g., `42/testuser1_20260518T091532Z.png`).
    - `tbm_participants.signature_url` = Storage 키 (URL 아닌 path).
    - 표시: 관리자 대시보드에서 클릭 시 signed URL 생성 (60s 만료) 후 표시.
  - **신규 Storage 버킷** (amended 2026-05-18 via research C3): `tbm-signatures` —
    **v1.0 = Option A (anon INSERT + key prefix 가드)**, v1.1 = Option B (Edge Function
    경유) 마이그. Option A 의 이유: 본 코드베이스 최초의 private 버킷이지만 Edge
    Function 경유 업로드는 PNG 바이너리 base64 + Edge Function 단계 페이로드 누적
    부담 → anon INSERT + storage RLS 의 path prefix 검증으로 충분.
    ```sql
    -- 013 내부 또는 별도 014_tbm_storage.sql
    INSERT INTO storage.buckets (id, name, public)
    VALUES ('tbm-signatures','tbm-signatures', false)
    ON CONFLICT (id) DO NOTHING;

    -- v1.0 Option A: anon INSERT 허용 + key prefix 가드
    -- (path = {session_id}/{user_id}_{timestamp}.png 형식 강제는 클라이언트 책임)
    CREATE POLICY "tbm_sig_insert_anon_v1_poc" ON storage.objects
      FOR INSERT TO anon, authenticated
      WITH CHECK (bucket_id = 'tbm-signatures');
    -- public read 없음 — 모든 read 는 signed URL 60s 만료 (service_role 발급)
    ```
    Plan 작성 시 (planner) Option A → B 마이그레이션 경로를 deferred 에 명시.
  - **PII 고려**: 서명은 PII 가능성 — `tbm-signatures` 는 private + signed URL only.
    PROJECT.md "신호 = 상태 신호 원칙" 의 보안 측면 일관.
  - **근거**: 산업안전 5월 PPT 데모 + 6월 현장 설치 즉시성 우선. v1.1 NFC/QR 옵션은
    deferred (CONTEXT.md `<deferred>` 참조).

### 미참여 대상 정의 (TBM-03 — "출근했으나 미참여")
- **D-04: v1.0 "그룹 worker 전원" 단순 정의 (출근 시스템 부재)**
  - **출근(attendance) 시스템 v1.0 부재** — 본 phase scope creep 회피 위해 별도
    phase 미신설. 대신 단순 SQL 로 "미참여 대상" 계산:
    ```sql
    -- 오늘 group_id=1 세션의 미참여 worker
    SELECT p.user_id, p.user_name
    FROM public.profiles p
    WHERE p.group_id = $session_group_id
      AND p.user_role IN ('worker','general_manager')   -- general_manager 도 참여 의무
      AND p.user_id NOT IN (
        SELECT user_id FROM public.tbm_participants WHERE session_id = $session_id
      )
      AND p.user_id != $leader_user_id;                  -- 리더 본인 제외
    ```
  - **v1.0 한계 인지**: 휴가/병가/외부근무 작업자도 "미참여" 로 집계됨 → 알림 fatigue
    위험. v1.0 5월 데모 한정 acceptable (testuser1 demo + 소수 작업자), v1.1 에서
    attendance 시스템 또는 휴가 캘린더 도입 후 정교화.
  - **근거**: ROADMAP "출근했으나" 는 PRD-UX 문구, 실제 구현 부담 회피. Phase 9 scope =
    TBM 자체 + 가벼운 미참여 알림. 명시적 출근 시스템은 별도 phase (v1.1+ 후속).

### 미참여 알림 시각 / 임계 (TBM-03)
- **D-05: pg_cron 1분 주기 + `expected_end_at + 30분` 임계 + Phase 4 D-09 알림 전이 원칙**
  - **마이그레이션 (`013_tbm_schema.sql` 후반부)**:
    ```sql
    -- (F) pg_cron job 'tbm_missed_attendance_minute'
    CREATE OR REPLACE FUNCTION public.tbm_missed_attendance_check() RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, extensions, net
    AS $$
    DECLARE r RECORD; sr_key TEXT; base_url TEXT;
    BEGIN
      -- Vault 로부터 service_role_key + edge_function_base_url 조회 (Phase 8 012 패턴)
      SELECT decrypted_secret INTO sr_key FROM vault.decrypted_secrets WHERE name='service_role_key';
      SELECT decrypted_secret INTO base_url FROM vault.decrypted_secrets WHERE name='edge_function_base_url';
      IF sr_key IS NULL OR base_url IS NULL THEN
        RAISE WARNING 'TBM cron: Vault sr_key or base_url missing — skip';
        RETURN;
      END IF;

      -- expected_end_at + 30분 경과 + missed_alert_at NULL 인 세션 1회 알림
      FOR r IN
        SELECT session_id, group_id, expected_end_at, leader_user_id
        FROM public.tbm_sessions
        WHERE expected_end_at + interval '30 minutes' < now()
          AND missed_alert_at IS NULL
          AND ended_at IS NULL                  -- 종료된 세션은 skip
      LOOP
        UPDATE public.tbm_sessions
          SET missed_alert_at = now()
          WHERE session_id = r.session_id;

        PERFORM net.http_post(
          url := base_url || '/functions/v1/notifications',
          headers := jsonb_build_object(
            'Authorization', 'Bearer '||sr_key,
            'Content-Type', 'application/json'),
          body := jsonb_build_object(
            'action','tbm-missed',
            'session_id', r.session_id,
            'group_id', r.group_id,
            'leader_user_id', r.leader_user_id)
        );
      END LOOP;
    END;
    $$;

    -- idempotent cron schedule (010 패턴)
    SELECT cron.unschedule('tbm_missed_attendance_minute')
      WHERE EXISTS (SELECT 1 FROM cron.job WHERE jobname='tbm_missed_attendance_minute');
    SELECT cron.schedule('tbm_missed_attendance_minute', '* * * * *',
      $$ SELECT public.tbm_missed_attendance_check(); $$);
    ```
  - **알림 전이 원칙 (Phase 4 D-09)**: 같은 세션의 미참여 알림은 1회만 발사
    (`missed_alert_at` 으로 dedup, Phase 8 `last_alert_at` 패턴과 동일). 30분 cooldown
    개념은 본 phase 에는 불필요 (단발 알림 + 세션 종료 시 reset 안 함 — 1일 1세션
    가정).
  - **임계 시각**:
    - 관리자가 세션 시작 시 `expected_end_at` 입력 (예: 09:00 시작 → 09:15 종료 예정).
    - 미참여 알림 시각 = `expected_end_at + 30분` (예: 09:45 알림 발사).
    - 30분 margin = 작업자가 늦게 도착해도 자율 참여 기회 + 알림 fatigue 회피.
  - **알림 수신자**: 미참여 worker **본인** + 세션 leader (manager) 도 함께 (1:N
    sendPushToUsers — Phase 8 패턴). leader 가 누가 빠졌는지 즉시 파악.
  - **근거**: pg_cron 1분 주기 = Phase 8 012 동일 패턴. 30분 margin = 단순 + 운영자
    조정 가능 (v1.1+ 별도 설정 화면).

### 관리자 대시보드 (TBM-03)
- **D-06: HomeActivity 신규 카드 "오늘 TBM 현황" + TbmDashboardActivity (manager only)**
  - **HomeActivity 카드 위치**: 기존 daily-safety-check 카드 위 또는 옆 (planner UX
    결정). `main_home.xml` 에 LinearLayout 추가 + ComposeView 임베드 (Phase 7
    HomeWorker 패턴 재사용).
  - **카드 내용** (3줄):
    1. **상태 badge**: 오늘 세션 status — "세션 없음" / "진행 중 ({checked}/{total})" /
       "완료" — 색상 회색/노랑/초록.
    2. **참여 카운트**: `{참여}/{대상}` — 예: `5/8 명` (D-04 의 대상 계산).
    3. **미참여 알림 상태**: 활성 시 "⚠ 미참여 3명에게 알림 발송됨 09:45".
  - **카드 클릭** → **TbmDashboardActivity** (신규, Compose):
    - 오늘 세션 정보 (작업유형, 시작시각, 예정종료시각, 리더, 위치).
    - 체크리스트 표시 (read-only — 관리자가 시작 시 입력한 상태 그대로) + 추가 체크
      가능 (실시간 갱신).
    - 참여자 목록: profile_image / 이름 / 체크인 시각 / 서명 thumbnail (클릭 → signed
      URL preview).
    - 미참여자 목록: 회색 표시 + "수동 알림" 버튼 (manager 가 즉시 재알림 가능).
    - "세션 종료" 버튼 (manager only).
  - **데이터 소스**: Realtime `tbm_sessions` / `tbm_checklists` / `tbm_participants`
    3 채널 (Phase 7 D-01 패턴, supabase-kt SDK 재사용). 실시간 참여 갱신.
  - **권한 가드**: `UserSession.userRole == MANAGER` 확인 후 진입 (Phase 7 패턴 일관).
    worker 가 deep-link 진입 시 권한 거부 토스트 + finish().
  - **근거**: 코드 경로 분리 SC #4 충족 — daily-safety-check Activity 와 완전 별도.
    Phase 7 의 watch card + dashboard 패턴 1:1 미러.

### 작업자 가이드 화면 (TBM-02)
- **D-07: HomeWorkerActivity watch card 아래 "오늘 TBM" 카드 + TbmWorkerActivity**
  - **HomeWorkerActivity 카드 위치**: 기존 워치 카드 (Phase 7 D-02 ComposeView 임베드)
    아래에 TBM 카드 ComposeView 추가. `main_home_worker.xml` 에 LinearLayout +
    `<ComposeView android:id="@+id/tbmCardCompose">`.
  - **카드 상태별 UI**:
    - **세션 없음** (오늘 group 의 `tbm_sessions` row 부재): 회색 "오늘 TBM 미시작"
      + 클릭 비활성.
    - **세션 active + 본인 미참여**: 노랑 "⚠ TBM 참여 필요 (예정 종료 09:15)" + 클릭
      → TbmWorkerActivity 즉시 진입.
    - **세션 active + 본인 참여 완료**: 초록 "✓ TBM 참여 완료 09:08" + 클릭 → 본인
      참여 내역 read-only 표시.
    - **세션 종료**: 회색 "오늘 TBM 종료 (09:30)" + 클릭 → 참여 내역 표시.
  - **TbmWorkerActivity** (신규, Compose):
    - 상단: 세션 정보 (작업유형 title, 리더 이름, 예정 종료시각).
    - 중단: 체크리스트 표시 (read-only, manager 가 입력한 체크 상태 그대로 표시).
      각 항목 옆에 ✓/✗ icon + note (있을 시).
    - 하단: 서명 영역 (D-03 SignatureCanvas) + "참여 확인" 버튼.
    - 참여 후: 서명 thumbnail + "참여 완료 09:08" 표시 + 버튼 disable.
  - **FCM trigger**: 세션 시작 (D-02) 시 그룹 worker 전원에게 FCM `tbm-started` 발사.
    payload `{action:'tbm-started', session_id, work_type}` → `MyFirebaseMessagingService`
    가 `data.type == 'tbm_alert'` 분기 → pendingIntent → HomeWorkerActivity 진입 또는
    TbmWorkerActivity 직접 진입 (extras 신뢰 X — DB 재조회 Phase 7 D-02 패턴).
  - **Realtime 갱신**: 본인 참여 row insert / 세션 종료 / 체크리스트 갱신 모두
    Realtime 구독 (Phase 7 패턴 재사용).
  - **근거**: HomeWorker 의 가벼운 통합 — 카드 1개만 추가, 별도 Activity 1개. Phase 7
    의 watch card → SafetyAlertsActivity 패턴 1:1 미러.

### Edge Function actions (TBM-01·02·03)
- **D-08: `notifications/index.ts` 에 4 case 추가 (Phase 4·7·8 패턴 일관)**
  - **신규 actions**: `tbm-start` / `tbm-checkin` / `tbm-end` / `tbm-missed`
  - **`tbm-start`** (manager → 세션 생성):
    - Payload: `{action, leader_user_id, group_id, work_type, expected_end_at, location?, notes?}`
    - 로직:
      1. `tbm_templates` 에서 work_type 조회 → checklist JSONB 가져오기.
      2. `tbm_sessions` insert (UNIQUE 충돌 시 409 "이미 오늘 세션 존재").
      3. `tbm_checklists` bulk insert (template.checklist 각 item 별 row).
      4. group worker 전원에게 `sendPushToUsers` 호출 (Phase 8 패턴):
         - 수신자 SELECT: `WHERE group_id = $group_id AND user_role IN ('worker','general_manager') AND user_id != $leader_user_id`
         - data.type = 'tbm_alert', action_in_app = 'tbm-started'
    - Response: `{ok, session_id, checklist_count, notified_count}`
  - **`tbm-checkin`** (worker → 참여 확정):
    - Payload: `{action, session_id, user_id, signature_url}`
    - 로직:
      1. `tbm_sessions` 존재 + `ended_at IS NULL` 검증 (404/410 if invalid).
      2. ownership 검증: `user_id` 의 group_id 가 session.group_id 와 일치 (Phase 7
         spoofing 차단 패턴, T-7-03 mitigation 재사용).
      3. `tbm_participants` insert (UNIQUE 충돌 시 200 idempotent "이미 참여").
    - Response: `{ok, participant_id, signed_at}`
  - **`tbm-end`** (manager → 세션 종료):
    - Payload: `{action, session_id, leader_user_id}`
    - 로직: leader_user_id == session.leader_user_id 검증 + `UPDATE tbm_sessions
      SET ended_at = now()`.
    - Response: `{ok, ended_at, participant_count}`
  - **`tbm-missed`** (pg_cron 호출, D-05):
    - Payload: `{action, session_id, group_id, leader_user_id}`
    - 로직:
      1. session 조회 + 미참여 worker 계산 (D-04 SQL).
      2. 미참여 worker 각 + leader_user_id 에게 `sendPushToUsers` 호출.
      3. payload data.type = 'tbm_alert', action_in_app = 'tbm-missed', missed_count.
    - Response: `{ok, missed_count, notified_count}`
    - **D-09 회귀 가드**: `notifications.insert()` 부재 (push-only, 상태 전이 책임은
      cron 의 `missed_alert_at` UPDATE).
  - **notifications insert 정책** (amended 2026-05-18 via research C1): **4 case 모두
    push-only** — `public.notifications` row insert 없음. Phase 8 D-09 회귀 가드 일관.
    상태 전이 책임은 `tbm_sessions.missed_alert_at` (cron) + `tbm_participants` insert
    (worker checkin) 이 가짐. notifications insert 회귀 가드: 4 case 호출 후
    `public.notifications` row 수 변화 = 0.
  - **재배포**: 1회 (`supabase functions deploy notifications`) — Phase 4·7·8 동일.
  - **curl smoke**: 4 actions × {정상 / 권한 부족 / 중복 / 누락 payload} = 8~12 케이스.
    Phase 8 4-smoke 패턴 동일.
  - **근거**: 단일 `notifications` 함수 안에 action-routing — Phase 4·7·8 이 모두 동일
    패턴. 신규 함수 분리 X (deploy 부담 + dispatch 통일성).

### 알림 채널 / FCM payload
- **D-09: `fcm_default_channel` 재사용 (Phase 8 Option B 동일) + data.type='tbm_alert'**
  - **channel_id 분리 X** (v1.0): Android 코드 변경 0. Phase 8 의 Option B 패턴 그대로 —
    `fcm_default_channel` 트레이에 표시. v1.1 에서 `tbm_alerts` 채널 분리 검토.
  - **data payload**:
    ```json
    { "type": "tbm_alert",
      "action_in_app": "tbm-started" | "tbm-missed" | "tbm-ended",
      "session_id": 42,
      "work_type": "electric" }
    ```
  - **MyFirebaseMessagingService 분기**: 기존 `watch_alert` 분기 옆에 `tbm_alert` 추가.
    `action_in_app` 별로 진입 Activity 분기 (tbm-started → TbmWorkerActivity 또는
    HomeWorkerActivity / tbm-missed → TbmWorkerActivity / tbm-ended → 표시만).
  - **extras 신뢰 X** (Phase 7 D-02 일관): Activity 진입 시 session_id 만 신뢰 + DB
    재조회로 권한/상태 확인.
  - **근거**: Phase 8 Option B 와 동일 — deadline 부재지만 Android 코드 변경 0 원칙
    + v1.0 단순성 유지.

### Claude's Discretion
- **체크리스트 항목 텍스트 워딩** — D-01 시드 5종은 일반 산업안전 표준 기반. 현장
  관리자가 추후 수정 가능. planner 가 카카오 알림톡 톤 또는 KOSHA 표준 참조 가능.
- **expected_end_at default 값** — manager 가 입력하지만 UI default = "15분 후"
  권장 (TBM 표준 길이). planner UX 결정.
- **SignatureCanvas 색상/굵기** — onSurface 4dp stroke default. Material3 theme
  연동. planner UX 결정.
- **TbmDashboardActivity 의 시계열 그래프** — 일자별 참여율 chart 는 v1.x. v1.0
  은 오늘 1일 view 만.
- **세션 종료 후 read-only mode 시점** — `ended_at IS NOT NULL` 즉시 모든 클라이언트
  read-only 전환. planner 가 lifecycle 결정.
- **참여 알림 (entry, not missed)** — worker 가 참여 시 leader 에게 "{name} 참여
  완료" FCM 발사 여부 — v1.0 한정 X (Realtime 대시보드 갱신만). v1.1+ 옵션.
- **다중 세션/다중 작업장** — v1.0 UNIQUE `(group_id, session_date)` 잠금. v1.1 에서
  `(group_id, location, session_date)` 또는 별도 schema 진화.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 9 직접 입력
- `.planning/REQUIREMENTS.md` §9 TBM 현장 작업자 가이드 (TBM-01·02·03) — 본 phase 의 요구 (line 147-159)
- `.planning/ROADMAP.md` Phase 9 섹션 (line 273-292) — Goal · 4 Success Criteria · Depends on (없음, 병렬)
- `.planning/PROJECT.md` — "신호 = 상태 신호 원칙" + "알림 전이 원칙" (Key Decisions, TBM 미참여 알림 1회 발사 근거)

### 패턴 원천 — 우선순위 순
- `.planning/phases/04-watch-j2208a-pipeline/04-CONTEXT.md` — **D-09 알림 전이 원칙** (본 phase D-05 의 미참여 알림 1회 발사 근거) + **D-11 FCM only** + **D-12 Edge Function action-routing**
- `.planning/phases/07-watch-app-bridge/07-CONTEXT.md` — **D-04b Edge Function 경유 write** (본 phase D-08 의 tbm-start/checkin/end/missed 패턴 원천) + **D-02 ComposeView 임베드** (본 phase D-06·D-07 의 HomeActivity/HomeWorker 카드 패턴) + **D-04 status badge 3-색상** (본 phase D-07 의 카드 상태별 UI 색상 일관)
- `.planning/phases/08-rtsp-camera/08-CONTEXT.md` — **D-03 pg_cron 1분 주기 + Vault sr_key** (본 phase D-05 의 tbm_missed_attendance_check 패턴 1:1 미러) + **sendPushToUsers (plural)** 사용법 + **Option B fcm_default_channel 재사용** (본 phase D-09 동일)
- `.planning/phases/08-rtsp-camera/08-04-SUMMARY.md` — Vault sr_key 시드 + Dashboard 수동 절차 (본 phase D-05 cron 도 동일 prerequisite)

### Watch / TBM 도메인 원천
- `docs/PROJECT_SPEC.md` §1.2 3대 위협 분류 (TBM 체크리스트 시드 work_type 5종 매핑)
- `docs/PROJECT_SPEC.md` §"산업안전관리 일지" — daily_safety_check 와 TBM 의 책임 분리 (본 phase SC #4)

### 기존 자산 / 재사용
- `app/src/main/java/com/example/smart_safety_management/HomeActivity.kt` — manager 메인 화면, TBM 카드 추가 위치 (D-06)
- `app/src/main/java/com/example/smart_safety_management/HomeWorkerActivity.kt` — worker 메인 화면, ComposeView 임베드 패턴 (D-07, Phase 7 D-02 패턴 재사용)
- `app/src/main/java/com/example/smart_safety_management/watch/` (전체 9 파일) — `tbm/` 패키지 1:1 미러 (D-06·D-07·D-08 클라이언트 코드 원형)
- `app/src/main/java/com/example/smart_safety_management/watch/SupabaseModule.kt` + `WatchRealtimeRepository.kt` — supabase-kt Realtime 3 채널 패턴 (본 phase 4 채널 동일)
- `app/src/main/java/com/example/smart_safety_management/watch/PairWatchSection.kt` — Edge Function 호출 패턴 (Retrofit + Bearer anon)
- `app/src/main/java/com/example/smart_safety_management/UserSession.kt` + `UserRole.kt` — manager/worker 권한 가드 (D-06·D-07)
- `supabase/functions/notifications/index.ts` (line 243-337 watch-pair / line 353+ camera-down) — 본 phase D-08 의 tbm-start/checkin/end/missed 4 case 추가 위치
- `supabase/functions/_shared/fcm.ts` (line 239 sendPushToUsers / line 288 sendPushToUser) — 본 phase D-08 의 tbm-started + tbm-missed manager+worker N명 push
- `supabase/functions/_shared/supabase.ts` — service_role admin client 헬퍼
- `supabase/migrations/010_watch_pipeline.sql` — 4 테이블 + pg_cron + RLS + Realtime publication 1:1 패턴 (D-01 의 013 가 동일 구조 미러)
- `supabase/migrations/011_watch_app_rls.sql` — v1.0 RLS narrowing 패턴 (D-01 의 RLS USING (true) 또는 narrowing 선택 근거)
- `supabase/migrations/012_cameras_health.sql` (line 100-180) — Vault sr_key + base_url + pg_cron 1분 주기 + SECURITY DEFINER + search_path 잠금 + EXCEPTION OTHERS 흡수 (본 phase D-05 의 tbm_missed_attendance_check 1:1 미러)
- `supabase/migrations/004_storage.sql` — Storage 버킷 정의 패턴 (D-03 의 `tbm-signatures` 신규 버킷 추가 위치)
- `app/src/main/java/com/example/smart_safety_management/MyApp.kt` + `MyFirebaseMessagingService` — supabase-kt 싱글톤 + FCM data.type 분기 (D-09)
- `app/build.gradle.kts` — supabase-kt 2.2.0 의존성 이미 추가됨 (Phase 7 D-01) — TBM 추가 의존성 0

### 테스트 / 검증
- `supabase/migrations/tests/sql/test_011_rls_isolation.sql` + `test_012_cameras_health_isolation.sql` — 본 phase 의 `test_013_tbm_isolation.sql` 작성 패턴
- `ai_agent/tests/` — 본 phase 무관 (Android + Edge Function only, ai_agent 코드 0 변경)

### testuser1 시드 + 기존 데이터
- testuser1 (`profiles.user_id`, manager 권한, group_id=1) — 본 phase 의 PoC manager.
  PoC 시나리오: testuser1 이 세션 시작 → group_id=1 의 다른 worker (시드 필요 또는
  testuser1 자신이 worker 흉내 — planner 결정) 가 참여 → 미참여 worker 알림 검증.
- **시드 worker 필요**: v1.0 PoC 한정 group_id=1 에 worker role 2~3명 시드 (planner
  가 `tests/seed_tbm_demo.py` 또는 SQL 작성).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`app/src/main/java/com/example/smart_safety_management/watch/`** (Phase 7 패키지, 9 main + 4 test 파일) — `tbm/` 패키지 1:1 미러 base. SupabaseModule (싱글톤) / Repository (3 채널 → 4 채널) / WatchCardComposable (status badge 3-색상) / SafetyAlertsScreen (LazyColumn + acknowledge) / PairWatchSection (Retrofit POST + status badge) 모두 TBM 도메인으로 rename + 4 채널 / signature canvas / 체크리스트 표시로 변용.
- **`HomeWorkerActivity.kt:48-60`** (Phase 7 ComposeView import + setupWatchCard) — 본 phase 는 동일 `setupTbmCard()` 함수 추가 + `main_home_worker.xml` 의 ComposeView id 1개 신규 (`tbmCardCompose`).
- **`HomeActivity.kt`** — manager 메인. Phase 7 에는 manager 카드 추가 없음. 본 phase D-06 가 첫 ComposeView 임베드 (worker 와 동일 패턴, XML 변경 + setupTbmCard 추가).
- **`supabase/functions/notifications/index.ts:243-337`** (watch-pair 패턴) — 본 phase D-08 의 4 case 추가 — payload 검증 + ownership 검증 + UPSERT + sendPushToUsers 호출.
- **`supabase/functions/notifications/index.ts:353-426`** (camera-down/recovered + sendPushToUsers plural) — 본 phase D-08 의 tbm-started/tbm-missed 가 동일 sendPushToUsers 호출 (1:N manager + worker).
- **`supabase/migrations/012_cameras_health.sql`** — Vault sr_key + base_url + pg_cron 1분 주기 + SECURITY DEFINER + EXCEPTION OTHERS — 본 phase 013 의 tbm_missed_attendance_check 가 1:1 미러 (cameras_healthcheck() 의 카메라→세션 도메인 swap).
- **`supabase/migrations/010_watch_pipeline.sql:124-145`** (pg_cron unschedule+schedule idempotent 패턴) — 본 phase 013 의 cron 등록 동일.
- **`UserSession.userRole` + `UserRole.MANAGER`** — Phase 9 의 권한 가드 (D-06 TbmDashboardActivity 진입 시).

### Established Patterns
- **마이그레이션 번호 컨벤션** — 001~012 사용. 본 phase = **013_tbm_schema.sql**.
- **Edge Function action-routing** — `case 'foo': handleFoo(req); break;` (Phase 4·7·8 동일). 본 phase D-08 가 4 case 추가.
- **service_role 만 write, anon 만 read (RLS)** — Phase 7 D-04b 정착. 본 phase D-01 동일.
- **Realtime publication ADD TABLE** — Phase 7 011 패턴. 본 phase 013 동일.
- **pg_cron + Vault + SECURITY DEFINER** — Phase 8 012 패턴. 본 phase 013 의 D-05 동일.
- **sendPushToUsers (plural) — manager+worker N명** — Phase 8 D-03 패턴. 본 phase D-08 의 tbm-started + tbm-missed 동일.
- **알림 전이 원칙 1회 발사 (last_alert_at / missed_alert_at)** — Phase 4 D-09. 본 phase D-05 `missed_alert_at` 컬럼이 동일 역할.
- **ComposeView 임베드 (XML/AppCompat Activity 에 Compose 카드 추가)** — Phase 7 D-02 정착. 본 phase D-06 (HomeActivity) + D-07 (HomeWorkerActivity) 모두 적용.
- **fcm_default_channel 재사용 (Option B)** — Phase 8 D-03. 본 phase D-09 동일 (Android 코드 변경 0).
- **MAC validation + ownership 검증 server-side** — Phase 7 D-04b watch-pair 패턴. 본 phase D-08 tbm-checkin 의 group_id ownership 검증 동일.

### Integration Points
- **Android → Edge Function**: Retrofit POST `/functions/v1/notifications` (action=tbm-*). Authorization Bearer anon + apikey 헤더. 본 phase 4 actions.
- **Android ↔ Supabase Realtime**: supabase-kt SDK postgresChangeFlow — 4 채널 (tbm_sessions / tbm_templates / tbm_checklists / tbm_participants). Phase 7 SupabaseModule 싱글톤 재사용.
- **Android → Supabase Storage**: signature PNG 업로드 (`tbm-signatures` 버킷). supabase-kt Storage SDK 또는 Retrofit POST `/storage/v1/object/...` (planner 결정).
- **pg_cron → notifications Edge Function**: net.http_post — Phase 8 패턴 1:1.
- **FCM → MyFirebaseMessagingService**: data.type='tbm_alert' 분기 → Activity 진입.
- **HomeActivity TBM 카드 ↔ TbmDashboardActivity**: ComposeView (카드) + Activity intent (대시보드).
- **HomeWorkerActivity TBM 카드 ↔ TbmWorkerActivity**: 동일 패턴 worker 측.

### 잠재 함정
- **UNIQUE (group_id, session_date) 충돌** — manager 가 같은 날 두 번 시작 시 409. Edge Function tbm-start 가 명시 응답 처리 + UI toast.
- **service_role_key Vault 미시드** — Phase 8 와 동일 Dashboard 수동 시드 prerequisite (이미 처리됨, 본 phase 도 cron 의존). 미시드 시 `tbm_missed_attendance_check` 가 RAISE WARNING + RETURN graceful skip (D-05 가드).
- **Storage 버킷 정책** — `tbm-signatures` private + service_role write only + signed URL read (60s). public read 활성화 금지 (PII).
- **Realtime 4 채널 트래픽** — 1 group × 1 day × 1 session = 트래픽 작음. 다중 그룹/세션 v1.1 에서 group_id filter 강제.
- **Compose Canvas → Bitmap memory 누수** — `dispose()` / `recycle()` 호출 필수 (planner 검증).
- **체크리스트 JSONB ↔ tbm_checklists row 동기화** — manager 가 세션 시작 후 템플릿
  수정 시 기존 세션 영향 X (snapshot row 보존). 단 template UPDATE 가 미래 세션에만
  반영됨 (의도된 동작).
- **expected_end_at timezone** — TIMESTAMPTZ 사용 (Phase 4 010 의 UTC immutability 교훈). Android 측 ZonedDateTime → ISO8601 with offset 전송.
- **leader 가 worker 흉내 (PoC)** — testuser1 manager 가 세션 시작 후 본인이 worker
  체크인 시도 → tbm_participants insert 가 manager 권한과 무관 가능. 의도된 동작 (시연 편의).
- **Vault sr_key 노출** — pg_cron 함수 내부 SQL 에 sr_key 가 직접 박히지 않음 (Phase 8
  012 가 vault.decrypted_secrets SELECT 패턴 정착). 본 phase 동일.

</code_context>

<specifics>
## Specific Ideas

- **"가벼운 통합" 정신** (Phase 7·8 일관) — 본 phase 는 TBM 3종 SC 충족이 목표지
  전사 안전관리 워크플로우 본격 구현 X. 마이그레이션 1개 + Edge Function 4 case +
  Android 화면 2개 + 카드 2개 = 한계. 출근 시스템 / 통계 / 다국어 / NFC·QR / 사진 첨부
  는 deferred.
- **알림 전이 원칙 일관 적용** (PROJECT.md Key Decision + Phase 4 D-09): TBM 미참여
  알림은 같은 세션 *1회만* 발사. `tbm_sessions.missed_alert_at` 으로 dedup.
- **신호 = 상태 신호 원칙** (PROJECT.md Key Decision): "참여" 상태가 raw 행 (signed_at
  timestamp) 이지만, 카드/대시보드는 추상화된 상태 라벨 ("진행 중 5/8", "완료").
- **수기 서명 PII 보호** — `tbm-signatures` 버킷 private + signed URL 60s 만료. PROJECT.md
  보안 일관성.
- **5월 PPT 데모 시나리오 통합**:
  - **시나리오**: "manager (testuser1) 가 오전 09:00 TBM 세션 시작 ('전기' 작업유형,
    5 체크 항목, 예정 종료 09:15) → 작업자 A·B 가 09:08 / 09:12 폰에서 체크인 (서명) →
    09:45 (expected_end_at + 30분) 에 작업자 C 가 미참여 → FCM 알림 발사 → manager
    대시보드에 미참여자 표시"
  - **PPT 슬라이드 활용**: HomeActivity 카드 캡처 + TbmDashboardActivity 참여자 grid
    캡처 + 작업자 서명 화면 캡처 + FCM 푸시 캡처. 3·4·7·8 시연 흐름과 함께 통합.
- **마감일 부재** — Phase 7 의 수요일 마감과 달리 무마감. ROADMAP estimate 3일. Phase 7
  Wave 4 deferred 상태 + Phase 4 04-04 24h 대기 상태에서 평행 진행 가능 (코드베이스
  분리). 6월 검단·포천 설치 전 안정화.

</specifics>

<deferred>
## Deferred Ideas

### v1.1 후속 phase
- **명시적 출근(attendance) 체크인 시스템** — TBM 의 "미참여 대상" 정교화. NFC 또는
  geofence 진입 시 자동 출근 처리. 본 phase D-04 의 "그룹 worker 전원" 단순 정의를
  대체. v1.1 별도 phase.
- **TBM NFC / QR 체크인 옵션** — D-03 의 수기 서명 + alternatives. NFC 태그 발급 +
  카드 리더 또는 작업자 폰 카메라 QR 스캔. v1.1+.
- **TBM 사진 첨부 (안전 장구 착용 사진)** — 작업자가 안전모/안전화 사진 1장 첨부.
  Storage 버킷 추가. v1.1+.
- **다중 작업장(workplace) 동시 운용** — UNIQUE `(group_id, session_date)` 완화. 1
  group × N location × M session/day. v1.1+.
- **세션 종료 후 manager 승인 워크플로우** — 현재는 작업자 서명 즉시 참여 완료. 승인
  단계 추가 시 `tbm_participants` 에 `approved_at` 컬럼 추가. v1.1+.
- **체크리스트 정규화** (`tbm_template_items` 분리) — D-01 의 JSONB array → 정규화
  테이블. 체크리스트 항목별 통계/risk_level 연동 가능. v1.1 LP-5 룰 seed 연동.

### Phase 6 DEMO / v1.x
- **TBM 통계 / 월별 참여율 리포트 / 시계열 chart** — TbmDashboardActivity 의 일자별
  view 추가 또는 별도 manager 화면. Phase 6 DEMO 또는 v1.x.
- **카카오톡 알림톡 / SMS 채널** — 미참여 알림을 카카오톡으로 추가 발송. Next-7 별도
  마일스톤.
- **TTS 음성 가이드** — TBM 체크리스트 음성 안내. Next-5 PTT 음성 트랙.
- **다국어 체크리스트** — 외국인 작업자 대응 (영어/베트남어/태국어). v1.x.

### Channel / FCM 분리
- **`tbm_alerts` Android NotificationChannel 분리** — 현재는 `fcm_default_channel`
  재사용 (D-09 Option B, Phase 8 동일). v1.1 채널 분리.

### Edge cases
- **체크리스트 항목별 자동 위험도 매핑** (LP-5 룰 seed 연동) — v1.1.
- **휴가 / 외부근무 작업자 제외 캘린더** — D-04 단순 정의의 알림 fatigue 회피. v1.1+.
- **참여 알림 (entry, not missed)** — worker 가 참여 시 leader 에게 즉시 push.
  현재는 Realtime 대시보드 갱신만 의존. v1.1+.
- **세션 시작 자동 (cron)** — 매일 09:00 자동 세션 생성. 현재는 manager 수동 시작. v1.1+.

### testuser1 시드 보강
- **group_id=1 에 worker role 2~3명 추가 시드** — PoC 다중 참여 검증. planner 가
  scripts/seed_tbm_demo.py 또는 SQL fixture 작성. 본 phase 의 plan 1 단계로 포함 가능.

</deferred>

---

*Phase: 09-tbm-worker-guide*
*Context gathered: 2026-05-18*
*Mode: --auto (autonomous, user directive: no clarifying questions)*
