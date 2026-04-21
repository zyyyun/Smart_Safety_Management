# ai_agent — P.A.S.S. CCTV 스냅샷 Agent (Next-4)

Supabase Edge Function(Deno)이 FFmpeg 를 돌릴 수 없어 외부 Python 프로세스로 분리.
로컬 개발 PC에서 10분 주기로 RTSP 카메라를 캡처해 Supabase Storage·DB에 적재한다.

## 파이프라인

```
cameras 테이블 (live_url_detail IS NOT NULL)
  │
  ▼
┌─────────── ai_agent (Python) ───────────┐
│  FFmpeg : RTSP → JPEG 1프레임           │
│  Supabase Storage : 업로드 (public URL) │
│  system/camera_capture Edge Function    │
│    · camera_captures INSERT             │
│    · 카메라당 최근 5장만 유지            │
└─────────────────────────────────────────┘
```

## 요구 사항

- Python 3.10+
- FFmpeg (PATH 등록 또는 `.env`의 `FFMPEG_BIN`에 절대경로 지정)
- Supabase service_role 키

## 최초 설정

```bash
cd ai_agent
cp .env.example .env        # Windows: copy .env.example .env
# .env 편집:
#   SUPABASE_URL=https://xbjqxnvemcqubjfflain.supabase.co
#   SUPABASE_SERVICE_ROLE_KEY=eyJhbGciOi...
pip install -r requirements.txt
```

## 실행

```bash
# 상시 실행 (10분 주기, 시작 즉시 1회 실행 후 반복)
python main.py

# 1회만 실행하고 종료 (디버깅)
python main.py --once
```

## 테스트 카메라 시드 — AI-Hub 스타일 레퍼런스 영상 대체

2026-04-21 기준 실기기 부재 + 공개 RTSP 데모 스트림(wowza, zephyr) 응답 불가 확인.
대안으로 **2025 레거시(`D:\2025_산업안전\산업안전\`)의 AI-Hub 스타일 참조 영상**을
로컬 파일 경로로 `cameras.live_url_detail`에 매핑해 카메라 소스를 시뮬레이션한다.

`snapshot.py`는 URL 스킴(`rtsp://`, `rtsps://`)만 감지해 RTSP 전용 옵션을 적용하고,
그 외는 일반 FFmpeg 입력으로 처리하므로 로컬 파일·HTTP·RTSP 모두 동일한 파이프라인으로 통과.

### 시드 적용

```bash
# Supabase SQL Editor 또는 psql
\i supabase/seeds/test_cameras.sql
```

### 매핑 구성 (5종 파일럿 AI 모델 대응)

| device_code | 레퍼런스 영상 | 검증 |
|---|---|---|
| TEST-CAM-01 | `발표자료용 영상/detection(fire, helmet).mp4` | 화재+안전모 |
| TEST-CAM-02 | `데이터/쓰러짐 영상.mp4` | 쓰러짐 |
| TEST-CAM-03 | `모델 7종/사람 탐지/input_video.mp4` | 사람 |
| TEST-CAM-04 | `모델 7종/지게차 탐지/test_forklift.gif` | 지게차 (GIF 정적 장면) |
| TEST-CAM-05 | `모델 7종/화재 탐지/input.mp4` | 화재 단독 |

### 한계

- **Android 라이브 재생 불가** : `live_url`은 시드에서 `NULL`로 비워짐.
  D6(ExoPlayer RTSP) 단계에서 mediamtx 등으로 로컬 MP4를 RTSP로 재송출 후 업데이트.
  현재 앱에서 "전경"/"현장" 영역은 "연결대기" 배지만 표시되지만, "현장캡쳐" 섹션은 정상 동작.
- **경로는 agent 실행 PC 기준** : 다른 PC에서 구동 시 시드 SQL 재적용 필요.
- **지게차는 GIF** : 매 스냅샷이 거의 동일한 장면 → 모델 학습은 가능하나 시각적 변화는 제한적.

## 로컬 검증 절차

1. `python main.py --once` 실행 → 로그에 `[OK] camera_id=X url=https://...` 확인
2. Supabase 대시보드 > Storage > `camera-captures/periodic/{camera_id}/` 에 JPG 생성 확인
3. SQL: `SELECT * FROM camera_captures WHERE event_type='PERIODIC' ORDER BY captured_at DESC LIMIT 5;`
4. 6회 이상 실행 후: 카메라당 row 개수가 5개로 제한되는지 확인

## 구성 요소

| 파일 | 역할 |
|---|---|
| `config.py` | `.env` 로드, Settings dataclass |
| `snapshot.py` | FFmpeg 래퍼 (RTSP → JPEG) |
| `supabase_client.py` | cameras 조회 / Storage 업로드 / Edge Function 호출 |
| `scheduler.py` | APScheduler 주기 실행, 카메라 병렬 처리 |
| `main.py` | CLI 엔트리포인트 |

## 레거시 대응

기존 `server/cron_scheduler.js`(Express + node-cron)의 포트이다.
FFmpeg 옵션(`-analyzeduration`, `-probesize`, `-frames:v 1`, `-q:v 2`, `-update 1`)을 그대로 이식.
보관정책(`camera-captures` 버킷·`camera_captures` 테이블에서 최근 5장) 역시 동일.

기존 Express 서버(`server/`)는 **참조용으로만 보존**하며 런타임으로 실행하지 않는다.
중복 실행 시 카메라당 스냅샷이 이중 적재되어 보관정책이 교차 삭제함.

## 후속 계획 (본 작업 범위 밖)

- Next-3 / LP-2: 본 agent에 YOLO 추론 모듈(ultralytics) 추가 → `system/create_ai_event` 호출
- Next-1: Firebase 서비스 계정 확보 후 Edge Function에서 FCM 발송
- 배포: 파일럿 현장 이전 시 로컬 PC → 미니 PC/NUC 설치

## 트러블슈팅

| 증상 | 원인 / 해결 |
|---|---|
| `SnapshotError: FFmpeg 실행 파일을 찾을 수 없습니다` | `FFMPEG_BIN=C:\ffmpeg\bin\ffmpeg.exe` 처럼 절대경로 지정 |
| `401 Unauthorized: service role key required` | service role 키 값이 `.env`에 정확히 들어갔는지, Edge Function이 재배포됐는지 확인 |
| Storage 업로드 403 | `camera-captures` 버킷 `public=true` 확인, 004_storage.sql 적용 여부 확인 |
| `httpx.ConnectError` | `SUPABASE_URL` 에 `https://` 프리픽스 포함 여부, 방화벽 확인 |
