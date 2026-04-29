# Smart Safety Management — Milestones

> 본 파일은 GSD `.planning/` 부트스트랩 (2026-04-29) 시점에 이미 출하된 작업을
> v0.1~v0.5 로 후행 기록. v1.0 부터 GSD 워크플로우로 정식 추적.

---

## Shipped

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

### v1.0 — 5월 PPT 데모

- **Status**: Planning
- **Started**: 2026-04-29
- **Target**: 5월 중순 (≈ 3주 critical path)
- **Goal**: 5월 PPT 데모에서 5종 AI 비전 + J2208A 워치 1인 파이프라인이 단일 시연
  흐름으로 통합 동작. 수요측 예산 편성 트리거 단일 목표.
- **Tracks**: 비전 (Phase 1·2·3 chained) + 워치 (Phase 4 병렬) + 평가/데모
  (Phase 5·6 통합)
- **Reference**: `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`
