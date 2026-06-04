# Smart Safety Management — Milestones

> 본 파일은 GSD `.planning/` 부트스트랩 (2026-04-29) 시점에 이미 출하된 작업을
> v0.1~v0.5 로 후행 기록. v1.0 부터 GSD 워크플로우로 정식 추적.

---

## Shipped

### v1.1 — 앱 전체 완성도 (scope-reduced close)

- **Status**: Shipped (scope-reduced)
- **Started**: 2026-05-23
- **Completed**: 2026-06-04
- **Duration**: ~13일
- **Phases complete**: 1/5 — Phase 11 (일관 시각 언어 정립)
- **Phases partial**: 2 — Phase 12 (TBM 재설계), Phase 13 성격의 quick fixes (일일점검 날짜 / AI 이벤트 화면)
- **Phases not completed**: 2 — Phase 15 Docker 전환, Phase 14 설치 사전 UAT
- **Requirements outcome**: strict GSD 기준 0/16 fully satisfied, code evidence 기준 8/16 partial-or-better. Known gaps accepted at close. See `.planning/milestones/v1.1-MILESTONE-AUDIT.md`.
- **Delivered / stabilized**:
  - Phase 11 UI 공통 토큰·컴포넌트·Setting scaffold 적용
  - TBM 다중 OPS, 작업자 제출 후 홈 이동, 세션 시작/종료 즉시 갱신, worker/manager Compose crash 안정화
  - 일일 안전점검 selected date 저장 mismatch fix
  - AI 이벤트 캡처가 Supabase Storage에 저장된 실제 capture URL을 반환하도록 보강
  - Drift X3 YOLO watch PowerShell 자동 실행/재시도 경로 보강
  - J2208A 워치 B-mode read loop, runtime state, stale callback 방지, pairing telemetry 억제 등 안정화
- **Known gaps accepted at close**:
  - Phase 11/12 formal `*-VERIFICATION.md` 없음
  - Phase 13/14/15 산출물 디렉터리 없음
  - Docker 요구사항은 실제 운영 방향인 visible PowerShell scheduled task와 충돌, 다음 마일스톤에서 정식 scope 결정 필요
  - KOSHA TBM PDF/export, 검단·포천 UAT, 3-role walkthrough 미완료
- **Archives**:
  - `.planning/milestones/v1.1-ROADMAP.md`
  - `.planning/milestones/v1.1-REQUIREMENTS.md`
  - `.planning/milestones/v1.1-MILESTONE-AUDIT.md`
- **Next milestone**: not opened. Run `$gsd-new-milestone` when ready.

---

### v1.0 — 5월 PPT 데모 (scope-reduced close)

- **Status**: Shipped (scope-reduced)
- **Started**: 2026-04-29
- **Completed**: 2026-05-22
- **Duration**: ~24일 (계획 critical path ≈ 3주 + 수요일 추가)
- **Phases completed**: 6/10 — Phase 1·2·3 (비전 5종 / 운영급 임계 + frames 연속 + bbox fusion) · Phase 8 (Drift X3 RTSP 실기기 검증 person conf 0.92 latency 3.16s) · Phase 9 (TBM 현장 작업자 가이드 4 테이블 + Edge Function 4 case + Android `tbm/` 12 main + 48 unit tests PASS) · Phase 10 (LAN RTSP auto-detect — PC service + TCP probe + reachable 전이 YOLO 자동 구동, ai_agent 31/31 PASS)
- **Phases partial**: 2 — Phase 4 (Wave 1·2 ✓ J2208A BLE notification S2~S4 + Supabase 적재, Wave 3 24h 실측 04-04 ⏸) · Phase 7 (07-01·02·03 ✓ 워치-앱 양방향 백엔드 + Android UI, 07-04 단축 PoC 시연 ⏸ deferred)
- **Phases deferred to v1.1**:
  - Phase 5 (평가 — 2단계 정량 지표) — not started, v1.1 이월 결정 (2026-05-22)
  - Phase 6 (데모 빌드 — 통합 시연·캡처·PPT) — not started, v1.1 이월 결정 (2026-05-22)
  - Phase 4 04-04 (24h 워치 실측) — user 의사결정 대기
  - Phase 7-04 (단축 PoC + E2E 시연) — 시연 환경 부재 (autonomous: false)
  - Phase 9-04 (1일 사이클 manual 시연) — 사용자 가용 시점 이연
- **Requirements validated**: 25/28 (89%) — DATA/MODEL/FUSION/BRIDGE/RTSP/TBM 트랙 전부 + WATCH Wave 1·2 부분. 미달: WATCH-04 (24h 운영) + EVAL-01·02·03 (Phase 5) + DEMO-01·02·03·04 (Phase 6)
- **Key milestones**:
  - 5종 AI 비전 detector E2E (`09f2764`) + 운영급 임계 복원 (Phase 1·2)
  - bbox fusion (지게차+사람 IoU / 헬멧 hardhat_is_on) — Phase 3 (22/22 pytest PASS)
  - Phase 8 Drift X3 실기기 RTSP-02 검증 (`48f09ac`) — person event_id=46 conf=0.92 latency 3.16s ≪ SC #2 10s
  - Phase 9 TBM 13 commits Wave 1·2·3 (`f044fac` → `bfd0cec`) — autonomous chain 성공
  - Phase 10 LAN RTSP auto-detect (2026-05-22) — pivot 후 same-LAN PC service path 잠금
- **Key decisions** (PROJECT.md 참조):
  - YOLO26 마이그레이션 시점 = 6월~ (검단·포천 설치 직전 LP-3 와 함께)
  - 신호 해석 원칙 (J2208A 플랜 §3) + 알림 전이 원칙 (§6) — 워치/비전 일관 적용
  - Phase 10 pivot (2026-05-22) — 모바일 frame sampler PoC → PC same-LAN auto-detect
- **PR**: #1 (zyyyun/Smart_Safety_Management, test→main) — 2026-05-22 생성
- **Scope reduction rationale**: 5월 PPT 데모 마감 (≈ 2026-05-15 계획) 이미 지난 상태 + Phase 5·6 의 정량 지표·PPT 통합 자료는 백엔드/인프라 검증과 분리하여 별 milestone (v1.1) 으로 정착이 회고·재투입 효율성 우위. 백엔드·인프라·실기기 검증의 v1.0 핵심 가치 (운영급 임계 + 룰 충실도 + 워치 1인 파이프라인 + Drift X3 + TBM + LAN 자동화) 는 모두 도달.
- **Next milestone**: v1.1 (6월 검단·포천 설치 + Phase 5·6 평가/PPT)

---

### v0.5 — LP-2 확장 4종 detector

- **Status**: Shipped
- **Key commits**: `09f2764`, `433eb58`
- **Summary**: YOLOv5/v8 dual `GenericYoloDetector` + `DETECTOR_CONFIGS` +
  `run_detection_cycle`. 4종 (화재 · 안전모 · 지게차 · 사람) 추가, 5종 모두
  `--once-detect` 1회 실행에서 detection_events 생성 검증. 레퍼런스 영상 4종
  자동 업로드 + cameras.live_url_detail 갱신 (`scripts/upload_reference_videos.py`).
  `migrations/008_event_types_extension.sql` 으로 '지게차 진입', '혼잡도 경고' 추가.
- **임시조치 (v1.0 에서 복원)**: fire/helmet `conf_thres` 0.10, helmet
  `target_classes` None, fall + general detector 동시 활성화 불가 (sys.path 충돌).

### v0.4 — Next-2/3 이미지 업로드 + 쓰러짐 AI

- **Status**: Shipped
- **Key commits**: `7458a9e` (Next-2), `62b51fd` (Next-3 / LP-2 1단계)
- **Summary**: 4 버킷 (profile-images / action-images / check-images /
  camera-captures) E2E 13 항목 체크리스트 통과 + 데이터 손실 버그 3건 수정.
  YOLOv7-pose 기반 쓰러짐 감지 E2E (FallDetector + 1분 슬로텍 + 쿨타임).

### v0.3 — Next-1 FCM 푸시 실전송 + Firebase 신 프로젝트

- **Status**: Shipped
- **Key commits**: `8e601eb`
- **Summary**: `_shared/fcm.ts` 헬퍼, RS256 JWT self-sign, 5종 훅 지점
  (`detection`, `location`, `devices`, `notifications`, `system`). 구
  `smart-safety-management-3cf43` (접근 불가) → 신 `smart-safety-2026` 이전.
  `service-account*.json` `.gitignore` 차단. testuser1 실기기 푸시 수신 검증됨.

### v0.2 — Next-4 CCTV 스냅샷 자동화

- **Status**: Shipped
- **Key commits**: `ad1e82d`, `25868a0`, `aefef0a`, `4b18bd4`, `0888c44`,
  `a7e0420`, `a5d53d0`
- **Summary**: 외부 Python agent → Storage 업로드 + Edge Function insert. 커스텀
  `SYSTEM_AGENT_SECRET` 인증, 레퍼런스 영상 경로 분리, stream_info 비재생 URL
  차단 (ANR 방지), InternalDetail VideoPlayer 무한 루프 재생.

### v0.1 — Supabase 풀 마이그레이션

- **Status**: Shipped (Phase 1-6, 2026-04-20 완료)
- **Reference**: `.claude/plans/playful-discovering-quiche.md` (외부 플랜 파일)
- **Summary**: Express+PG+Firebase → Supabase 전면 이전. DB · Auth · Storage ·
  13 Edge Functions / 56+ 액션 · Android 인터셉터 · API 통합 테스트. 카카오맵
  연동 (Native App Key + REST API Key + 플랫폼 등록). test 브랜치 SplashActivity
  로그인 우회 복원 (`f5283ec`).

---

## Active

_(no active milestone — v1.1 시작 시 `/gsd-new-milestone` 으로 신설)_
