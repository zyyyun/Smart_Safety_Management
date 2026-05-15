# Phase 8: Drift X3 RTSP 실시간 카메라 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in 08-CONTEXT.md.

**Date:** 2026-05-15
**Phase:** 08-rtsp-camera
**Deadline:** 2026-05-20 (수요일, D-5)
**Areas discussed:** Frame 추출 방식, 재연결 backoff, 헬스체크 알림 채널·임계, 합성 검증 전략

---

## Frame 추출 방식 (RTSP-01)

| Option | Description | Selected |
|---|---|---|
| 기존 ffmpeg subprocess (snapshot.capture) 재사용 | URL scheme 자동 인식, 변경 surface 최소, 4 detector 무변경 | ✓ |
| cv2.VideoCapture + ffmpeg fallback | latency ↓ but OpenCV build RTSP backend 의존성 검증 필요 | |
| ffmpeg-python lib 도입 | API 깔끔 but 신규 의존성 + 학습 비용 | |

**User's choice:** 기존 ffmpeg subprocess 재사용
**Notes:** scheduler.py 가 이미 RTSP-aware (`rtsp_url = camera["live_url_detail"]`), snapshot.capture() 가 ffmpeg subprocess 라 자동 처리. RTSP 전용 ffmpeg 플래그 (`-rtsp_transport tcp -timeout 5000000 -fflags nobuffer`) 만 snapshot.py 내부에서 URL scheme 분기로 추가.

---

## 재연결 backoff (RTSP-03)

| Option | Description | Selected |
|---|---|---|
| snapshot.capture 내부 + 1s→3s→9s exponential 3회 | 13초 내 복구 시도, 포기 시 SnapshotError, scheduler 다음 cycle 재시도 | ✓ |
| scheduler 외부 + 고정 5s 3회 | backoff 결정점 한 곳, scheduler 코드에 명시 | |
| 둘 다 (snapshot + scheduler 1회 외부) | 이중 안전망, 코드 복잡 | |

**User's choice:** snapshot.capture 내부 + 1s→3s→9s
**Notes:** ffmpeg subprocess 실패 시 snapshot.capture() 내부에서 retry (총 3회, 1+3+9=13초). 포기 시 SnapshotError raise → scheduler 의 기존 except 패턴 (line 84 `[SNAPSHOT_ERR]` 로그) 자동 catch → 1분 cycle 후 자동 재시도.

---

## 헬스체크 알림 (RTSP-03)

| Option | Description | Selected |
|---|---|---|
| FCM 관리자 알림 + 5분 임계 | _shared/fcm.ts 재사용, Phase 4·7 일관, 알림 전이 원칙 | ✓ |
| FCM + 운영 로그 + 10분 | 보수적 임계, 추가 로그 path | |
| 로그 only + 5분 | alert fatigue 우려 시 보수적 | |

**User's choice:** FCM 관리자 알림 + 5분
**Notes:** cameras.last_frame_at < now() - 5min 이면 manager FCM. pg_cron 1분 주기 healthcheck job + cameras.health_state ENUM (ok/degraded/down/unknown) + last_alert_at cooldown 30분. notifications/index.ts 에 case 'camera-down' + 'camera-recovered' 추가.

---

## 합성 검증 전략 (RTSP-02 deferred + RTSP-01·03 합성 검증)

| Option | Description | Selected |
|---|---|---|
| Mediamtx + 기존 mp4 → RTSP 합성 스트림 | reference-videos mp4 publish, cameras 등록, scheduler cycle, mediamtx kill 로 backoff 테스트 + 5분 무수신 → FCM 테스트 | ✓ |
| Public RTSP test stream (Big Buck Bunny) | 가장 단순 but detection 검증 의미 약함, 재연결 제어 불가 | |
| RTSP-01/03 코드만 작성, 합성/실측 모두 deferred | 가장 빠름 but integration bug 위험 | |

**User's choice:** Mediamtx + 합성 RTSP 스트림
**Notes:** 사용자 PC `/c/Users/ANNA/Desktop/mediamtx/bin` 에 mediamtx 이미 설치됨. scripts/start_rtsp_mock.sh 신규 — mediamtx config + ffmpeg `-stream_loop -1` publish (5종 mp4). cameras.live_url_detail 임시 갱신 또는 신규 카메라 row → 1 detection cycle + detection_events 검증 + mediamtx kill backoff 검증 + 5분 무수신 FCM 검증. RTSP-02 (Drift X3 실기기) deferred.

---

## Claude's Discretion

- ffmpeg RTSP 플래그 정확 값 (timeout 5초, fflags nobuffer)
- 재연결 backoff 시간 미세 조정 (1s→3s→9s default, 단축 가능)
- pg_cron schedule (1분 default)
- healthcheck 함수 구현 (pg_net 직접 호출 default, Edge Function fallback)
- last_frame_at 갱신 빈도 (매 capture default, cycle 단위 dedup 가능)
- mediamtx config 위치 (scripts/mediamtx.yml default)
- ffmpeg publish loop 옵션 (-stream_loop -1 -re default)
- 시연 카메라 row (기존 임시 변경 vs 신규 추가)

## Deferred Ideas

- Drift X3 실기기 검증 (RTSP-02 실측 ≤10s 지연) → 6월 LP-3
- 다중 카메라 동시 RTSP 부하 테스트 → v1.x
- GPU 가속 / hardware-encoded H.264 → v1.x
- RTCP/SDP 메타데이터, ONVIF discovery → v1.x
- Adaptive bitrate / multi-resolution → v1.x
- LP-3 RTSP 실카메라 → v1.1
- 운영 대시보드 (cameras.health_state grid) → Phase 6 또는 v1.x
- 1 RTSP → N detector fan-out → v1.x
- mediamtx 영구 운영 (systemd / docker-compose) → v1.x
- LP-5 룰 seed DB → v1.1
- 운영자 SQL UPDATE 로 health 임계 보정 → v1.1
