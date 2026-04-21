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

## 테스트 카메라 시드

공개 RTSP 샘플을 `cameras` 테이블에 넣으려면:

```bash
# Supabase SQL Editor 또는 psql
\i supabase/seeds/test_cameras.sql
```

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
