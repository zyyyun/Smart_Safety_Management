# ai_agent — P.A.S.S. CCTV 스냅샷 + AI 감지 Agent

Supabase Edge Function(Deno)이 FFmpeg·PyTorch 를 돌릴 수 없어 외부 Python 프로세스로 분리.
로컬 개발 PC에서 두 가지 잡을 동시 실행한다.

## 파이프라인

```
cameras 테이블 (live_url_detail IS NOT NULL)
  │
  ├── [10분 주기 스냅샷 — Next-4]
  │   FFmpeg → JPEG → camera-captures/periodic/ → camera_captures(PERIODIC)
  │
  └── [1분 주기 쓰러짐 감지 — Next-3 / LP-2]
      FALL_ENABLED_CAMERA_IDS 의 카메라만 대상
      FFmpeg 1프레임 → YOLOv7-w6-pose → rule 기반 분류
      is_fall=True 시:
        camera-captures/detection/ 업로드
        → system/create_ai_event → detection_events(WARNING/쓰러짐) + notifications
      카메라당 FALL_COOLDOWN_MIN 분 쿨타임 (중복 방지)
```

## 요구 사항

- **Python 3.10 또는 3.11** (3.13은 torch 공식 wheel 없음)
- FFmpeg (PATH 또는 `FFMPEG_BIN` env)
- Supabase service_role 키 + SYSTEM_AGENT_SECRET
- (선택) NVIDIA GPU + CUDA — 10x 이상 빠른 추론
- 쓰러짐 모델 가중치 `yolov7-w6-pose.pt` (154MB, 2025 레거시 폴더 참조)

## 최초 설정

### 1) Edge Function 공유 비밀 등록

system Edge Function은 `SYSTEM_AGENT_SECRET` 환경변수와 일치하는 요청만 허용.
Supabase 프로젝트 비밀로 등록한 뒤 agent도 동일 값을 사용해야 함.

```bash
# 새 비밀 생성 (예시 — 64자 URL-safe)
python -c "import secrets; print(secrets.token_urlsafe(48))"

# Supabase에 등록 (한 번만)
supabase secrets set SYSTEM_AGENT_SECRET="<위에서 생성한 값>"

# system 함수 재배포 (secrets 적용)
supabase functions deploy system --no-verify-jwt
```

### 2) `.env` 작성

```bash
cd ai_agent
cp .env.example .env        # Windows: copy .env.example .env
# .env 편집:
#   SUPABASE_URL=https://xbjqxnvemcqubjfflain.supabase.co
#   SUPABASE_SERVICE_ROLE_KEY=<legacy service_role JWT>
#   SYSTEM_AGENT_SECRET=<위 1)에서 생성한 값과 동일>
pip install -r requirements.txt
```

> ⚠ **service_role 키 포맷 주의** — 2026-04 이후 Supabase 신규 키는 `sb_secret_xxx`
> 포맷이나, Storage SDK는 아직 legacy JWT(`eyJ...` 시작)를 요구. `supabase projects api-keys`
> 출력 중 `type=legacy` 행의 service_role 키를 사용.

## 실행

```bash
# 상시 실행 (스냅샷 10분 주기 + 쓰러짐 감지 1분 주기)
python main.py

# 스냅샷만 (쓰러짐 감지 비활성)
python main.py --no-fall

# 스냅샷 1회만 (디버깅)
python main.py --once

# 쓰러짐 감지 1회만 (모델 로드 + 추론 검증)
python main.py --once-fall
```

## 쓰러짐 감지 모듈 (Next-3 / LP-2)

### 가중치 설정

`.env` 의 `FALL_MODEL_WEIGHTS` 가 실제 파일을 가리키는지 확인.
기본은 2025 레거시 폴더 직접 참조:

```
FALL_MODEL_WEIGHTS=D:/2025_산업안전/산업안전/모델 7종/쓰러짐 탐지/yolov7-w6-pose.pt
```

YOLOv7 소스 파일(`models/`, `utils/`)은 `yolov7_fork/` 안에 복사되어 있다.
체크포인트 피클이 `models.yolo.Model` 등을 내장하므로 폴더명을 바꾸지 말 것.

### acceptance gate — 영상에서 쓰러짐 감지 확인

스케줄러를 돌리기 전에 실제 영상에서 감지되는지 검증:

```bash
python scripts/verify_fall_on_video.py
# ✅ 쓰러짐 감지 확인: E02_001.mp4 t=XXX.Xs conf=0.85
```

FAIL 이면 `FALL_IMG_SIZE`, `FALL_CONF_THRES`, 영상 해상도부터 확인.

### 감지 대상 카메라 제어

`.env` 의 `FALL_ENABLED_CAMERA_IDS` 에 camera_id 를 쉼표로 나열.
기본값 `2` (TEST-CAM-02, 쓰러짐 레퍼런스). 다른 카메라 추가하려면 `2,5` 등.

### 쿨타임

연속 감지 중복 방지. 카메라당 `FALL_COOLDOWN_MIN` (기본 10분). agent 재시작 시 초기화 — 재시작 직후 중복 감지 1건은 허용 범위.

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
