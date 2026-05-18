# Phase 9: TBM 현장 작업자 가이드 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-18
**Phase:** 09-tbm-worker-guide
**Mode:** `--auto` (autonomous — user directive: "no clarifying questions")
**Areas discussed:** Schema design, Session lifecycle, Check-in method, Missed-attendance target, Missed-alert threshold, Manager dashboard, Worker guide screen, Edge Function actions, FCM payload

---

## Schema design (TBM-01)

| Option | Description | Selected |
|--------|-------------|----------|
| A. JSONB checklist in tbm_templates | 단일 row per work_type, checklist 는 JSONB array. tbm_checklists 는 세션별 체크 상태만. | ✓ |
| B. Normalized tbm_template_items | 별도 정규화 테이블, item-level 통계 가능. | |
| C. Free-form text only | 체크리스트 매번 manager 가 수동 입력. | |

**Choice rationale (auto):** A — v1.0 단순성 우선. 5종 시드 (fire/electric/height/heavy/general)
는 ROADMAP §"화재 위험·전기·고소·중량물 등" 그대로 매핑. JSONB → 정규화는 v1.1 LP-5 룰
seed 연동 시 진화 경로 보존.

**Notes:** 마이그레이션 번호 = **013** (012 cameras_health 다음). RLS 패턴은 Phase 7 011
narrowing 미러 또는 USING (true) — write 는 Edge Function service_role 만. Realtime
publication 4 테이블 ADD.

---

## Session lifecycle (TBM-02)

| Option | Description | Selected |
|--------|-------------|----------|
| A. Manager-led, worker-joins | 관리자가 세션 생성 + FCM trigger → worker 가 본인 폰에서 참여. | ✓ |
| B. Worker-led TBM | 작업자가 세션 시작. | |
| C. Free join (no leader) | 그날의 작업장 단위 공유 세션. | |

**Choice rationale (auto):** A — 산업안전 TBM 관례 (관리자 주재) + Phase 7 의 manager →
worker FCM trigger 패턴 일관. UNIQUE `(group_id, session_date)` v1.0 잠금 (1 group × 1
session/day).

**Notes:** 다중 작업장 / 오전·오후 분리는 v1.1 deferred. UNIQUE 충돌 시 Edge Function 이
409 응답.

---

## Check-in method (TBM-02 — "NFC/QR/수기 서명 중 1")

| Option | Description | Selected |
|--------|-------------|----------|
| A. 수기 서명 (Compose Canvas → PNG → Storage) | 외부 의존성 0, 모든 기기 동작. | ✓ |
| B. NFC | 하드웨어 의존, 일부 저가 기기 미지원, 카드/태그 발급 필요. | |
| C. QR (manager 표시 + worker 스캔) | 양 단말 운용 부담, 카메라 권한, 사이트 검증 부재. | |

**Choice rationale (auto):** A — 5월 PPT 데모 + 6월 현장 설치 즉시성 우선. PII 위험은
`tbm-signatures` Storage 버킷 private + signed URL 60s 만료로 mitigate. v1.1 에서
NFC/QR alternatives 추가.

**Notes:** SignatureCanvas Composable — Compose Canvas 100% (Path 누적). Storage 키
컨벤션: `{session_id}/{user_id}_{timestamp}.png`. tbm_participants.method 컬럼은 v1.0
'signature' default + CHECK constraint 로 향후 nfc/qr/manual 확장 가능 (스키마 진화 0).

---

## Missed-attendance target definition (TBM-03 — "출근했으나 미참여")

| Option | Description | Selected |
|--------|-------------|----------|
| A. 명시적 출근 체크인 시스템 추가 | scope creep — 별도 phase 필요. | |
| B. 그룹 worker 전원 단순 정의 | profiles.group_id + user_role = worker/general_manager. | ✓ |
| C. devices.last_comm_at / location_logs 추론 | 워치 미착용 작업자에 부정확. | |

**Choice rationale (auto):** B — v1.0 출근 시스템 부재 + scope creep 회피. ROADMAP 의
"출근했으나" 는 PRD-UX 문구, 실제 구현은 단순 SQL. 알림 fatigue (휴가/병가 포함) 위험은
v1.0 5월 데모 한정 acceptable. v1.1 출근 시스템 별도 phase.

**Notes:** SQL — `SELECT user_id FROM profiles WHERE group_id = $1 AND user_role IN
('worker','general_manager') AND user_id NOT IN (참여자) AND user_id != $leader`.

---

## Missed-alert threshold (TBM-03)

| Option | Description | Selected |
|--------|-------------|----------|
| A. per-session expected_end_at + 30분 | manager 가 세션별 입력, pg_cron 1분 주기. | ✓ |
| B. 시스템 글로벌 default (09:00 → 09:30) | 단순 but 작업 시간 다양성 부재. | |
| C. tbm_templates 에 default 시각 + session override | 복잡 over-design. | |

**Choice rationale (auto):** A — 관리자가 매일 자유 설정 + pg_cron 1분 주기 + 30분
margin (작업자 자율 참여 기회 + 알림 fatigue 회피). Phase 8 012 cameras_healthcheck
pg_cron 패턴 1:1 미러.

**Notes:** Phase 4 D-09 알림 전이 원칙 적용 — `tbm_sessions.missed_alert_at` 으로 1회
dedup. 알림 수신자 = 미참여 worker 본인 + 세션 leader (1:N sendPushToUsers, Phase 8 패턴).

---

## Manager dashboard (TBM-03)

| Option | Description | Selected |
|--------|-------------|----------|
| A. HomeActivity 신규 카드 + TbmDashboardActivity | daily-safety-check 카드와 동일 위계. | ✓ |
| B. NoticeActivity 와 통합 | 공지 + TBM 혼합. | |
| C. SettingActivity 메뉴 추가 | 관리 메뉴 깊은 위치. | |

**Choice rationale (auto):** A — daily-safety-check 와 코드 경로 분리 (SC #4) +
가시성. ComposeView 임베드 + 별도 Activity (Phase 7 D-02 패턴 1:1 미러).

**Notes:** TbmDashboardActivity = manager only (UserSession.userRole 가드). Realtime 4
채널 (tbm_sessions / templates / checklists / participants). 참여자 grid + 미참여자
"수동 알림" 버튼 + "세션 종료" 버튼.

---

## Worker guide screen (TBM-02)

| Option | Description | Selected |
|--------|-------------|----------|
| A. HomeWorkerActivity 카드 + TbmWorkerActivity + FCM trigger | watch card 아래 신규 카드. | ✓ |
| B. 별도 메뉴 진입만 | FCM 없이 작업자 자율 진입. | |
| C. FCM 알림으로만 trigger | 카드 부재. | |

**Choice rationale (auto):** A — 카드 항상 표시 (상태별 4 색상) + FCM trigger (관리자
시작 시 즉시 알림). Phase 7 의 watch card → SafetyAlertsActivity 패턴 1:1 미러.

**Notes:** 카드 상태 — 세션 없음 (회색) / 본인 미참여 (노랑) / 본인 참여 완료 (초록) /
세션 종료 (회색). MyFirebaseMessagingService data.type='tbm_alert' 분기 → action_in_app
별 Activity 분기. extras 신뢰 X, DB 재조회 (Phase 7 D-02 일관).

---

## Edge Function actions (TBM-01·02·03)

| Option | Description | Selected |
|--------|-------------|----------|
| A. 단일 notifications/index.ts + 4 case 추가 (tbm-start/checkin/end/missed) | Phase 4·7·8 패턴 일관. | ✓ |
| B. 신규 tbm/index.ts 함수 분리 | deploy 부담 + dispatch 분산. | |

**Choice rationale (auto):** A — Phase 4 watch-alert, Phase 7 watch-ack/pair, Phase 8
camera-down/recovered 모두 단일 notifications 함수 안 action-routing 누적. 본 phase 도
동일 위치에 4 case 추가. deploy 1회.

**Notes:** ownership 검증 = Phase 7 watch-pair 의 group_id 정합 검증 (T-7-03 spoofing
차단 패턴 재사용). UNIQUE 충돌 시 idempotent (200) 또는 409 (이미 다른 세션 충돌).
curl smoke 4 actions × 2~3 케이스 = ~10 케이스 (Phase 8 4-smoke 패턴 확장).

---

## FCM payload / channel (TBM-03)

| Option | Description | Selected |
|--------|-------------|----------|
| A. fcm_default_channel 재사용 (Option B) + data.type='tbm_alert' | Android 코드 변경 0. | ✓ |
| B. 신규 'tbm_alerts' NotificationChannel 분리 | 트레이 구분 + Android 코드 변경. | |

**Choice rationale (auto):** A — Phase 8 D-03 의 Option B 동일. v1.0 Android 코드
변경 0 원칙 + v1.1 채널 분리 deferred. action_in_app = 'tbm-started' / 'tbm-missed' /
'tbm-ended' 로 클라이언트 분기.

**Notes:** MyFirebaseMessagingService 의 watch_alert 분기 옆에 tbm_alert 분기 추가
(line 변경 ~5줄). pendingIntent — action_in_app 별 Activity 진입 (extras session_id
만 신뢰 + DB 재조회).

---

## Claude's Discretion

다음 항목은 planner 에 위임 (실제 구현 시 결정):

- 체크리스트 항목 텍스트 워딩 (KOSHA 표준 또는 카카오 알림톡 톤)
- expected_end_at default 값 ("15분 후" 권장)
- SignatureCanvas 색상/굵기 (onSurface 4dp default)
- TbmDashboardActivity 시계열 차트 (v1.x deferred)
- 세션 종료 후 read-only mode lifecycle 정확한 시점
- 참여 entry 알림 (worker 참여 시 leader push) — v1.0 X, v1.1+ 옵션
- 다중 세션/다중 작업장 schema 진화 (v1.1 UNIQUE 완화)

---

## Deferred Ideas

### v1.1 후속 phase
- 명시적 출근(attendance) 체크인 시스템
- TBM NFC / QR 체크인 옵션
- TBM 사진 첨부 (안전 장구 착용 사진)
- 다중 작업장(workplace) 동시 운용 (UNIQUE 완화)
- 세션 종료 후 manager 승인 워크플로우
- 체크리스트 정규화 (`tbm_template_items` 분리, LP-5 룰 seed 연동)

### Phase 6 DEMO / v1.x
- TBM 통계 / 월별 참여율 리포트 / 시계열 chart
- 카카오톡 알림톡 / SMS 채널 (Next-7)
- TTS 음성 가이드 (Next-5 PTT)
- 다국어 체크리스트
- `tbm_alerts` Android NotificationChannel 분리

### Edge cases
- 체크리스트 항목별 자동 위험도 매핑 (LP-5 연동)
- 휴가 / 외부근무 작업자 제외 캘린더
- 참여 알림 (entry, not missed)
- 세션 시작 자동 (cron) — 매일 09:00 자동 생성

### testuser1 시드 보강
- group_id=1 에 worker role 2~3명 추가 시드 (PoC 다중 참여 검증)

---

## Mode notes (--auto)

본 phase 는 사용자 directive "no clarifying questions" 에 따라 `--auto` 모드로 진행됨.
모든 gray area 는 Claude 가 다음 우선순위로 선택:

1. **이전 phase 패턴 일관성** — Phase 4·7·8 의 established decisions 1:1 미러
2. **v1.0 5월 PPT 데모 안정성** — 외부 의존성 0, 시연 가능성 우선
3. **scope 보존** — 새 capability 추가 X, ROADMAP §9 의 4 SC 충족만
4. **PROJECT.md Key Decisions 일관성** — 알림 전이 / 신호 상태 / 보안 / 가벼운 통합
5. **v1.1+ 진화 경로 보존** — JSONB → 정규화, 단일 체크인 → multi-method, 그룹 worker
   전원 → 명시적 출근 시스템

사용자가 본 CONTEXT.md 검토 후 reject / amend 시 즉시 반영 (Phase 7·8 D-XX amended
패턴 동일).
