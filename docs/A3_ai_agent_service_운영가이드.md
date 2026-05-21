# A.3 ai_agent Windows Service 운영 가이드

**적용일**: 2026-05-21
**Plan ref**: `C:\Users\ANNA\.claude\plans\tbm-linear-dragonfly.md` A.3
**대상**: PC 가 무인 24/7 worker 로 부팅 자동 시작되어야 하는 검단/포천 시연·운영 PC

## 한 줄 요약

`scripts\ai_agent_service.ps1 -Action Install` 한 번 → 이후 PC 재부팅해도 사용자 조작 0 으로 `ai_agent/main.py` 가 백그라운드 실행.

---

## 사전 준비

1. **관리자 권한 PowerShell** (설치/제거/start/stop/restart 시 필요).
2. **NSSM** — 다음 중 하나면 됨:
   - Windows 11 = 자동 (`winget install NSSM.NSSM` 을 스크립트가 시도)
   - `scripts\_nssm\nssm.exe` 수동 배치
   - `choco install nssm` 사전 실행
3. **Python 경로** = `C:\Users\ANNA\miniconda3\python.exe` 기본값. 다른 환경이면 `-PythonPath` 로 override.
4. **`ai_agent\.env`** 가 정상 로드 가능한 상태 (FFMPEG_BIN 등 절대경로 권장).

---

## 명령어

### 설치 (1회)
```powershell
# 관리자 PowerShell
powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Install
```
- 현재 로그인한 사용자(`.\ANNA`) 권한으로 등록 → **GPU 접근 보존**.
  - LocalSystem 으로 돌리면 PyTorch 가 CUDA 못 봐서 silent CPU fallback. 절대 ObjectName=LocalSystem 변경 금지.
- 비밀번호 1회 입력 프롬프트 (Windows 계정 비밀번호).
- 서비스 자동 시작 + 부팅 시 자동 시작 + 실패 시 15초 후 재시작 설정.

### 상태 확인 (no admin)
```powershell
powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Status
```
출력:
- Status (Running/Stopped)
- PID, Uptime, Memory
- **`ai_agent.log` last write 시각** ← 진짜 동작 증거
- stderr.log 사이즈 (>0 이면 import 에러 가능성)

### 로그 보기 (no admin)
```powershell
powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Logs
```
3종 로그를 한 번에 tail:
- `logs\ai_agent.log` — Python 구조화 로그 (정상이면 여기만 보면 됨)
- `logs\ai_agent_service.stderr.log` — torch import / dotenv 파싱 에러 (Python 가 _configure_logging 도달 전 죽으면 여기만 단서)
- `logs\ai_agent_service.stdout.log` — print() 잔존물

### 제어
```powershell
# 관리자 PowerShell
powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Restart
powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Stop
powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Start
```

### 제거
```powershell
# 관리자 PowerShell
powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Uninstall
```
서비스만 제거 + `logs/` 폴더 보존.

---

## 왜 로그 파일이 2개인가 (의도된 설계)

| 파일 | 출처 | 용도 |
|---|---|---|
| `logs/ai_agent.log` | Python `RotatingFileHandler` (`main.py:_configure_logging`) | 정상 동작 시 모든 검출/스케줄/Supabase 이벤트. 5MB × 5 rotation. |
| `logs/ai_agent_service.stdout.log` | NSSM raw stdout | print() 잔존물, ultralytics 자동 다운로드 진행률 |
| `logs/ai_agent_service.stderr.log` | NSSM raw stderr | `torch` import 에러, `dotenv` 파싱 에러, **`_configure_logging` 도달 이전 사망 단서** |

> **"서비스 Running 인데 ai_agent.log 가 비어있다" = 거의 항상 stderr.log 에 답이 있다.**

---

## 부팅 자동 시작 검증 (설치 직후 1회 권장)

```powershell
# 1. 설치 직후 서비스 동작 확인
powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Status

# 2. PC 재부팅
shutdown /r /t 5

# 3. 부팅 후 다시 로그인 → 콘솔/PowerShell 켜지 않은 상태에서 다시 Status
powershell -ExecutionPolicy Bypass -File scripts\ai_agent_service.ps1 -Action Status
#    Status=Running + ai_agent.log last write 가 1분 이내여야 정상
```

E2E 검증 (선택):
```powershell
# RTSP 카메라 1개 1 cycle 시연 → detection_events 신규 row 자동 발생 (서비스 동작 보강 증거)
.\scripts\demo_rtsp_real_camera.ps1 -CameraIds 3 -NumCycles 1
```

---

## 트러블슈팅

### "Service Running 인데 검출이 안 됨"
1. `-Action Logs` 로 stderr.log 확인 → CUDA/torch 에러 잡기
2. `Get-CimInstance Win32_Service -Filter "Name='SmartSafetyAiAgent'"` 의 `StartName` 가 `.\ANNA` 인지 확인. `LocalSystem` 이면 GPU 못 봄 → uninstall 후 재설치.

### "Service 가 부팅 시 안 켜짐"
- PC 전원 옵션: "고성능, Sleep Never" 권장. Sleep 진입 시 service 잠시 정지 후 wake 시 복귀.
- `Get-Service SmartSafetyAiAgent | Select-Object StartType` 값이 `Automatic` 인지 확인. 아니면:
  ```powershell
  Set-Service -Name SmartSafetyAiAgent -StartupType Automatic
  ```

### "한글 경로 mojibake (`?` 또는 깨진 글자)"
설치 스크립트가 Install 시 자동 점검. 발견 시 안내:
```powershell
# 한 번만 수동 수정
$short = (New-Object -ComObject Scripting.FileSystemObject).GetFolder('D:\2026_산업안전\Smart_Safety_Management\ai_agent').ShortPath
nssm set SmartSafetyAiAgent AppDirectory $short
nssm restart SmartSafetyAiAgent
```

### "winget install NSSM.NSSM 실패"
- 인터넷 없는 PC: `nssm.exe` 를 https://nssm.cc/download (win64) 에서 받아 `scripts\_nssm\nssm.exe` 에 배치 → 재실행.

---

## 변경된 파일

- `ai_agent/main.py` — `_configure_logging` 가 console + RotatingFileHandler 둘 다 설치 (A.3.2)
- `scripts/ai_agent_service.ps1` — 신규 (A.3.1)
- `scripts/_nssm/README.md` — 신규 (NSSM 배치 안내)
- `logs/.gitkeep` — 신규 (로그 디렉토리 보장)
- `.gitignore` — `logs/*.log`, `scripts/_nssm/*.exe` 제외 추가
- `docs/A3_ai_agent_service_운영가이드.md` — 본 문서

## Out of scope (의도적 제외)

- **A.3.3 RTSP 자동 발견** — plan 에서도 "A.2.3 폰 측 등록만으로 충분" 명시. A.2 (QR 페어링) 와 함께 진행 예정.
- **GPU 모니터링/알람** — 별도 plan.
- **Service health Slack/FCM push** — 운영 안정화 후 검토.
