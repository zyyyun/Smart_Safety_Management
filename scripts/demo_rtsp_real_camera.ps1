# =============================================================================
# Phase 8 RTSP-02 — Drift X3 실기기 시연 자동화 스크립트
# =============================================================================
# 더블클릭 또는 PowerShell 에서 실행:
#   .\scripts\demo_rtsp_real_camera.ps1
#
# 동작:
#   1. ai_agent/.env 자동 로드 (SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY 등)
#   2. cameras 1·2·3·4·5 모두 live_url_detail → rtsp://192.168.0.13/live PATCH
#   3. ai_agent main.py --once-detect 를 N cycle 연속 실행
#      (-NumCycles default 5 — fire frames_required=5 충족용)
#   4. 신규 detection_events 출력 + 지연 측정
#   5. cameras 5 모두 mp4 URL 원복 (PATCH)
#   6. capture 이미지 URL 출력 (브라우저에서 직접 확인 가능)
#
# 옵션:
#   -RtspUrl       : 카메라 RTSP URL (default rtsp://192.168.0.13/live)
#   -NumCycles     : scheduler 반복 횟수 (default 5, fire 5연속 룰 충족)
#   -CycleInterval : cycle 간 대기 (default 2초)
#   -SkipRestore   : mp4 원복 skip (디버깅용, 시연 후엔 반드시 원복!)
#   -CameraIds     : RTSP 점프할 camera_id 배열 (default 1..5)
#
# Phase 1 fire `frames_required=5` 룰 충족용 — 1 cycle 만 돌리면 silent drop.
# helmet=3, fire=5, forklift=1, person=1.
#
# 시연 절차 (사용자):
#   1. 카메라 IP (default 192.168.0.13) PC 와 같은 네트워크 확인
#   2. drift_test.py 로 카메라 화면 1회 사전 확인 (선택)
#   3. 카메라 앞 stage 구성 (본인 + 화재 이미지 + 안전모 등)
#   4. 본 스크립트 실행 → 60초 후 결과 콘솔 출력 + capture URL
# =============================================================================

[CmdletBinding()]
param(
    [string]$RtspUrl = "rtsp://192.168.0.13/live",
    [int]$NumCycles = 5,
    [int]$CycleInterval = 2,
    [switch]$SkipRestore,
    [int[]]$CameraIds = @(1, 2, 3, 4, 5)
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Phase 8 RTSP-02 — Drift X3 실기기 시연 자동화 스크립트" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host " RtspUrl        : $RtspUrl"
Write-Host " CameraIds      : $($CameraIds -join ', ')"
Write-Host " NumCycles      : $NumCycles (fire frames_required=5 충족용)"
Write-Host " CycleInterval  : $CycleInterval 초"
Write-Host " SkipRestore    : $SkipRestore"
Write-Host ""

# ─────────────────────────────────────────────────────────────────────
# Step 0 — env 로드
# ─────────────────────────────────────────────────────────────────────
$envPath = Join-Path $RepoRoot "ai_agent\.env"
if (-not (Test-Path $envPath)) {
    Write-Host "[!] ai_agent\.env 파일 부재. 실행 중단." -ForegroundColor Red
    Write-Host "    Supabase env (URL + SERVICE_ROLE_KEY) 가 필요합니다." -ForegroundColor Red
    exit 1
}
Get-Content $envPath | ForEach-Object {
    if ($_ -match '^\s*([^#=]+)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
    }
}
$SR = $env:SUPABASE_SERVICE_ROLE_KEY
$URL = $env:SUPABASE_URL
if (-not $SR -or -not $URL) {
    Write-Host "[!] SUPABASE_URL 또는 SUPABASE_SERVICE_ROLE_KEY 미설정. ai_agent\.env 확인." -ForegroundColor Red
    exit 1
}
$env:OPENCV_FFMPEG_CAPTURE_OPTIONS = "rtsp_transport;tcp"
Write-Host "[+] env 로드 완료 (SUPABASE_URL=$($URL.Substring(0, [Math]::Min(40,$URL.Length)))...)" -ForegroundColor Green
$headers = @{"apikey"=$SR; "Authorization"="Bearer $SR"}
$headersPatch = @{"apikey"=$SR; "Authorization"="Bearer $SR"; "Content-Type"="application/json"; "Prefer"="return=representation"}

# ─────────────────────────────────────────────────────────────────────
# Step 1 — cameras 백업 (원복용)
# ─────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "[Step 1] cameras 백업 (원복용)" -ForegroundColor Yellow
$camFilter = ($CameraIds | ForEach-Object { "$_" }) -join ","
$camsBefore = Invoke-RestMethod -Method Get -Uri "$URL/rest/v1/cameras?camera_id=in.($camFilter)&select=camera_id,live_url_detail&order=camera_id" -Headers $headers
$backupMap = @{}
foreach ($c in $camsBefore) {
    $backupMap[$c.camera_id] = $c.live_url_detail
    Write-Host "  camera_id=$($c.camera_id) ← $($c.live_url_detail.Substring(0, [Math]::Min(80, $c.live_url_detail.Length)))..." -ForegroundColor Gray
}

# ─────────────────────────────────────────────────────────────────────
# Step 2 — cameras N개 모두 RTSP 점프
# ─────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "[Step 2] cameras → RTSP 점프 ($RtspUrl)" -ForegroundColor Yellow
$patchBody = '{"live_url_detail":"' + $RtspUrl + '"}'
foreach ($id in $CameraIds) {
    $null = Invoke-RestMethod -Method Patch -Uri "$URL/rest/v1/cameras?camera_id=eq.$id" -Headers $headersPatch -Body $patchBody
    Write-Host "  camera_id=$id ✓" -ForegroundColor Green
}

# ─────────────────────────────────────────────────────────────────────
# Step 3 — detection_events 사전 최대 event_id 기록 (신규 row 식별)
# ─────────────────────────────────────────────────────────────────────
$preMax = Invoke-RestMethod -Method Get -Uri "$URL/rest/v1/detection_events?order=event_id.desc&limit=1&select=event_id" -Headers $headers
$preMaxId = if ($preMax.Count -gt 0) { $preMax[0].event_id } else { 0 }
Write-Host ""
Write-Host "[Step 3] PRE_MAX event_id = $preMaxId" -ForegroundColor Yellow

# ─────────────────────────────────────────────────────────────────────
# Step 4 — scheduler N cycle 연속 실행
# ─────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "[Step 4] scheduler --once-detect × $NumCycles cycles" -ForegroundColor Yellow
Write-Host "  (사용자: 카메라 앞에서 stage 안정 유지)" -ForegroundColor Magenta
$cycleStartUtc = (Get-Date).ToUniversalTime()
$python = "C:\Users\ANNA\miniconda3\python.exe"
$mainPy = Join-Path $RepoRoot "ai_agent\main.py"
Push-Location (Join-Path $RepoRoot "ai_agent")
for ($i = 1; $i -le $NumCycles; $i++) {
    Write-Host "  cycle $i/$NumCycles 시작 ..." -ForegroundColor Cyan
    $cycleOut = & $python $mainPy --once-detect 2>&1
    # [DETECT] line 추출
    $detectLines = $cycleOut | Select-String -Pattern "\[DETECT\]|\[FUSION\]|\[FALL\]"
    foreach ($line in $detectLines) {
        Write-Host "    $line" -ForegroundColor Green
    }
    if ($i -lt $NumCycles) {
        Start-Sleep -Seconds $CycleInterval
    }
}
Pop-Location
$cycleEndUtc = (Get-Date).ToUniversalTime()
Write-Host "  완료 ($([Math]::Round(($cycleEndUtc - $cycleStartUtc).TotalSeconds, 1))초)" -ForegroundColor Green

# ─────────────────────────────────────────────────────────────────────
# Step 5 — 신규 detection_events 검증
# ─────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "[Step 5] 신규 detection_events (event_id > $preMaxId)" -ForegroundColor Yellow
$newEvents = Invoke-RestMethod -Method Get -Uri "$URL/rest/v1/detection_events?event_id=gt.$preMaxId&select=event_id,camera_id,device_name,accuracy,risk_level,detected_at,capture_id&order=event_id.asc" -Headers $headers
Write-Host "  신규 row: $($newEvents.Count) 건" -ForegroundColor $(if ($newEvents.Count -gt 0) { 'Green' } else { 'Yellow' })

if ($newEvents.Count -gt 0) {
    foreach ($e in $newEvents) {
        Write-Host "    event_id=$($e.event_id) camera=$($e.camera_id) ($($e.device_name)) accuracy=$([Math]::Round($e.accuracy,3)) risk=$($e.risk_level) detected_at=$($e.detected_at)" -ForegroundColor Green
    }

    # capture URL 표시
    Write-Host ""
    Write-Host "[Step 5b] Capture 이미지 URL (브라우저에 붙여넣으면 직접 확인 가능)" -ForegroundColor Yellow
    $capIds = ($newEvents | ForEach-Object { $_.capture_id } | Where-Object { $_ -ne $null }) -join ","
    if ($capIds) {
        $caps = Invoke-RestMethod -Method Get -Uri "$URL/rest/v1/camera_captures?capture_id=in.($capIds)&select=capture_id,camera_id,image_url,event_type" -Headers $headers
        foreach ($c in $caps) {
            Write-Host "    [capture_id=$($c.capture_id)] camera=$($c.camera_id) event_type='$($c.event_type)'" -ForegroundColor Cyan
            Write-Host "    $($c.image_url)" -ForegroundColor White
        }
    }

    # 지연 측정 — camera 별 last_frame_at 와 detected_at 비교
    Write-Host ""
    Write-Host "[Step 5c] 지연 측정 (capture → DB insert, SC #2 ≤10s)" -ForegroundColor Yellow
    foreach ($e in $newEvents) {
        $cam = Invoke-RestMethod -Method Get -Uri "$URL/rest/v1/cameras?camera_id=eq.$($e.camera_id)&select=last_frame_at" -Headers $headers
        if ($cam.Count -gt 0 -and $cam[0].last_frame_at) {
            try {
                $capUtc = [DateTimeOffset]::Parse($cam[0].last_frame_at).UtcDateTime
                $detUtc = [DateTime]::SpecifyKind([DateTime]::Parse($e.detected_at), [DateTimeKind]::Utc)
                $latency = ($detUtc - $capUtc).TotalSeconds
                $verdict = if ($latency -le 10 -and $latency -ge 0) { "PASS" } else { "WARN" }
                $color = if ($verdict -eq "PASS") { "Green" } else { "Yellow" }
                Write-Host "    event_id=$($e.event_id) capture=$($cam[0].last_frame_at) → insert=$($e.detected_at) → 지연 $([Math]::Round($latency, 2))s [$verdict]" -ForegroundColor $color
            } catch {
                Write-Host "    event_id=$($e.event_id) 지연 측정 실패: $($_.Exception.Message)" -ForegroundColor Gray
            }
        }
    }
} else {
    Write-Host "  [!] 신규 row 0 — 가능한 원인:" -ForegroundColor Yellow
    Write-Host "      - 카메라 시야에 검출 가능한 객체 부재" -ForegroundColor Yellow
    Write-Host "      - fire frames_required=5 미충족 (NumCycles 늘려보세요)" -ForegroundColor Yellow
    Write-Host "      - RTSP 연결 실패 → cameras.last_frame_at 미갱신" -ForegroundColor Yellow
}

# ─────────────────────────────────────────────────────────────────────
# Step 6 — cameras 원복
# ─────────────────────────────────────────────────────────────────────
Write-Host ""
if (-not $SkipRestore) {
    Write-Host "[Step 6] cameras 원복" -ForegroundColor Yellow
    foreach ($id in $CameraIds) {
        $orig = $backupMap[$id]
        if ($orig) {
            $restoreBody = ConvertTo-Json @{ live_url_detail = $orig } -Compress
            $null = Invoke-RestMethod -Method Patch -Uri "$URL/rest/v1/cameras?camera_id=eq.$id" -Headers $headersPatch -Body $restoreBody
            Write-Host "  camera_id=$id → 원복 ✓" -ForegroundColor Green
        }
    }
    # 검증
    $camsAfter = Invoke-RestMethod -Method Get -Uri "$URL/rest/v1/cameras?camera_id=in.($camFilter)&select=camera_id,live_url_detail&order=camera_id" -Headers $headers
    $stillRtsp = $camsAfter | Where-Object { $_.live_url_detail -match "^rtsp://" }
    if ($stillRtsp.Count -eq 0) {
        Write-Host "  [✓] cameras $($CameraIds -join '·') 모두 mp4 원복 확인" -ForegroundColor Green
    } else {
        Write-Host "  [!] 일부 cameras 가 여전히 RTSP — 수동 확인 필요:" -ForegroundColor Red
        $stillRtsp | ForEach-Object { Write-Host "    camera_id=$($_.camera_id) live_url=$($_.live_url_detail)" -ForegroundColor Red }
    }
} else {
    Write-Host "[Step 6] SkipRestore=on — cameras 원복 SKIP (수동 원복 필요!)" -ForegroundColor Yellow
    Write-Host "         원복 SQL:" -ForegroundColor Yellow
    foreach ($id in $CameraIds) {
        Write-Host "         UPDATE cameras SET live_url_detail = '$($backupMap[$id])' WHERE camera_id = $id;" -ForegroundColor Yellow
    }
}

# ─────────────────────────────────────────────────────────────────────
# Step 7 — 요약
# ─────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " 시연 완료 — Phase 8 RTSP-02" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " 신규 detection_events: $($newEvents.Count) 건"
Write-Host " 총 소요: $([Math]::Round(((Get-Date).ToUniversalTime() - $cycleStartUtc).TotalSeconds, 1))초"
Write-Host ""
if ($newEvents.Count -gt 0) {
    Write-Host " 📸 capture 이미지에 bbox + label 그려져 있음 (scheduler annotate_capture_with_bbox)" -ForegroundColor Green
    Write-Host " 📝 시연 슬라이드 evidence:" -ForegroundColor Green
    Write-Host "    - event_id $($newEvents[0].event_id)..$($newEvents[-1].event_id) (DB 영구 적재)"
    Write-Host "    - capture URL public (위 Step 5b)"
    Write-Host "    - 지연 측정 (위 Step 5c, ≤10s SC #2 검증)"
}
Write-Host ""
