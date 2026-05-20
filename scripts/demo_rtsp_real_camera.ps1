# =============================================================================
# Phase 8 RTSP-02 - Drift X3 real device demo automation script
# =============================================================================
# Double-click or run from PowerShell:
#   .\scripts\demo_rtsp_real_camera.ps1
#
# Steps:
#   1. Auto-load ai_agent/.env (SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY)
#   2. PATCH cameras 1..5 live_url_detail -> rtsp://192.168.0.13/live
#   3. Run ai_agent main.py --once-detect for N cycles
#      (-NumCycles default 5, satisfies fire frames_required=5)
#   4. Print new detection_events + latency measurement
#   5. Restore cameras 1..5 to original mp4 URLs (PATCH)
#   6. Print capture image URLs (direct browser access)
#
# Options:
#   -RtspUrl       : camera RTSP URL (default rtsp://192.168.0.13/live)
#   -NumCycles     : scheduler iterations (default 5, fire 5-frame rule)
#   -CycleInterval : wait between cycles in seconds (default 2)
#   -SkipRestore   : skip mp4 restore (debug only, manual SQL needed!)
#   -CameraIds     : which camera_ids to patch (default 1..5)
#
# Pre-flight (user):
#   1. Camera IP (default 192.168.0.13) reachable on same WiFi as PC
#   2. Optional: run drift_test.py once to verify camera stream
#   3. Stage in front of camera (person + fire image + helmet + etc.)
#   4. Execute this script -> ~60s -> evidence URLs in console
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
Write-Host " Phase 8 RTSP-02 - Drift X3 Real Device Demo" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host " RtspUrl        : $RtspUrl"
Write-Host " CameraIds      : $($CameraIds -join ', ')"
Write-Host " NumCycles      : $NumCycles (satisfies fire frames_required=5)"
Write-Host " CycleInterval  : $CycleInterval seconds"
Write-Host " SkipRestore    : $SkipRestore"
Write-Host ""

# ----------------------------------------------------------------------
# Step 0 - Load env from ai_agent/.env
# ----------------------------------------------------------------------
$envPath = Join-Path $RepoRoot "ai_agent\.env"
if (-not (Test-Path $envPath)) {
    Write-Host "[!] ai_agent\.env not found. Aborting." -ForegroundColor Red
    Write-Host "    Need SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY." -ForegroundColor Red
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
    Write-Host "[!] SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY missing in ai_agent\.env" -ForegroundColor Red
    exit 1
}
$env:OPENCV_FFMPEG_CAPTURE_OPTIONS = "rtsp_transport;tcp"
Write-Host "[+] env loaded (SUPABASE_URL=$($URL.Substring(0, [Math]::Min(50,$URL.Length)))...)" -ForegroundColor Green
$headers = @{"apikey"=$SR; "Authorization"="Bearer $SR"}
$headersPatch = @{"apikey"=$SR; "Authorization"="Bearer $SR"; "Content-Type"="application/json"; "Prefer"="return=representation"}

# ----------------------------------------------------------------------
# Step 1 - Backup cameras (for restore)
# ----------------------------------------------------------------------
Write-Host ""
Write-Host "[Step 1] Backup cameras (for restore)" -ForegroundColor Yellow
$camFilter = ($CameraIds | ForEach-Object { "$_" }) -join ","
$urlBackup = "$URL/rest/v1/cameras?camera_id=in.($camFilter)" + "&" + "select=camera_id,live_url_detail" + "&" + "order=camera_id"
$camsBefore = Invoke-RestMethod -Method Get -Uri $urlBackup -Headers $headers
$backupMap = @{}
foreach ($c in $camsBefore) {
    $backupMap[$c.camera_id] = $c.live_url_detail
    $truncated = if ($c.live_url_detail.Length -gt 80) { $c.live_url_detail.Substring(0, 80) + "..." } else { $c.live_url_detail }
    Write-Host "  camera_id=$($c.camera_id) <- $truncated" -ForegroundColor Gray
}

# ----------------------------------------------------------------------
# Step 2 - PATCH cameras to RTSP URL
# ----------------------------------------------------------------------
Write-Host ""
Write-Host "[Step 2] PATCH cameras -> RTSP ($RtspUrl)" -ForegroundColor Yellow
$patchBody = '{"live_url_detail":"' + $RtspUrl + '"}'
foreach ($id in $CameraIds) {
    $null = Invoke-RestMethod -Method Patch -Uri "$URL/rest/v1/cameras?camera_id=eq.$id" -Headers $headersPatch -Body $patchBody
    Write-Host "  camera_id=$id OK" -ForegroundColor Green
}

# ----------------------------------------------------------------------
# Step 3 - Record PRE_MAX event_id (to identify new rows)
# ----------------------------------------------------------------------
$urlPreMax = "$URL/rest/v1/detection_events?order=event_id.desc" + "&" + "limit=1" + "&" + "select=event_id"
$preMax = Invoke-RestMethod -Method Get -Uri $urlPreMax -Headers $headers
$preMaxId = if ($preMax.Count -gt 0) { $preMax[0].event_id } else { 0 }
Write-Host ""
Write-Host "[Step 3] PRE_MAX event_id = $preMaxId" -ForegroundColor Yellow

# ----------------------------------------------------------------------
# Step 4 - Run scheduler N cycles
# ----------------------------------------------------------------------
Write-Host ""
Write-Host "[Step 4] scheduler --once-detect x $NumCycles cycles" -ForegroundColor Yellow
Write-Host "  (User: keep stage stable in front of camera)" -ForegroundColor Magenta
$cycleStartUtc = (Get-Date).ToUniversalTime()
$python = "C:\Users\ANNA\miniconda3\python.exe"
$mainPy = Join-Path $RepoRoot "ai_agent\main.py"
# 2026-05-20 fix: Python ai_agent logging defaults to stderr. PowerShell 5.1 with
# $ErrorActionPreference='Stop' treats EACH stderr line from native exe as
# NativeCommandError → halts script on first log line. Workaround: switch to
# 'Continue' for the subprocess call only, then restore.
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
Push-Location (Join-Path $RepoRoot "ai_agent")
try {
    for ($i = 1; $i -le $NumCycles; $i++) {
        Write-Host "  cycle $i/$NumCycles start ..." -ForegroundColor Cyan
        # 2>&1 redirect — stderr lines become ErrorRecord objects in $cycleOut.
        # Select-String stringifies each item so [DETECT] line capture still works.
        $cycleOut = & $python $mainPy --once-detect 2>&1
        # Force string conversion before Select-String (avoid ErrorRecord pitfalls)
        $detectLines = $cycleOut | ForEach-Object { $_.ToString() } | Select-String -Pattern "\[DETECT\]|\[FUSION\]|\[FALL\]"
        foreach ($line in $detectLines) {
            Write-Host "    $line" -ForegroundColor Green
        }
        if ($i -lt $NumCycles) {
            Start-Sleep -Seconds $CycleInterval
        }
    }
} finally {
    Pop-Location
    $ErrorActionPreference = $prevEAP
}
$cycleEndUtc = (Get-Date).ToUniversalTime()
$elapsedSec = [Math]::Round(($cycleEndUtc - $cycleStartUtc).TotalSeconds, 1)
Write-Host "  done (${elapsedSec}s elapsed)" -ForegroundColor Green

# ----------------------------------------------------------------------
# Step 5 - Verify new detection_events
# ----------------------------------------------------------------------
Write-Host ""
Write-Host "[Step 5] New detection_events (event_id > $preMaxId)" -ForegroundColor Yellow
$urlNew = "$URL/rest/v1/detection_events?event_id=gt.$preMaxId" + "&" + "select=event_id,camera_id,device_name,accuracy,risk_level,detected_at,capture_id" + "&" + "order=event_id.asc"
$newEvents = Invoke-RestMethod -Method Get -Uri $urlNew -Headers $headers
$newCount = $newEvents.Count
$color = if ($newCount -gt 0) { 'Green' } else { 'Yellow' }
Write-Host "  new rows: $newCount" -ForegroundColor $color

if ($newCount -gt 0) {
    foreach ($e in $newEvents) {
        $acc = [Math]::Round($e.accuracy, 3)
        Write-Host "    event_id=$($e.event_id) camera=$($e.camera_id) ($($e.device_name)) accuracy=$acc risk=$($e.risk_level) detected_at=$($e.detected_at)" -ForegroundColor Green
    }

    # capture image URLs
    Write-Host ""
    Write-Host "[Step 5b] Capture image URLs (open in browser)" -ForegroundColor Yellow
    $capIds = ($newEvents | ForEach-Object { $_.capture_id } | Where-Object { $_ -ne $null }) -join ","
    if ($capIds) {
        $urlCaps = "$URL/rest/v1/camera_captures?capture_id=in.($capIds)" + "&" + "select=capture_id,camera_id,image_url,event_type"
        $caps = Invoke-RestMethod -Method Get -Uri $urlCaps -Headers $headers
        foreach ($c in $caps) {
            Write-Host "    [capture_id=$($c.capture_id)] camera=$($c.camera_id) event_type='$($c.event_type)'" -ForegroundColor Cyan
            Write-Host "    $($c.image_url)" -ForegroundColor White
        }
    }

    # latency measurement
    Write-Host ""
    Write-Host "[Step 5c] Latency (capture -> DB insert, SC #2 must be <=10s)" -ForegroundColor Yellow
    foreach ($e in $newEvents) {
        $cam = Invoke-RestMethod -Method Get -Uri "$URL/rest/v1/cameras?camera_id=eq.$($e.camera_id)&select=last_frame_at" -Headers $headers
        if ($cam.Count -gt 0 -and $cam[0].last_frame_at) {
            try {
                $capUtc = [DateTimeOffset]::Parse($cam[0].last_frame_at).UtcDateTime
                $detUtc = [DateTime]::SpecifyKind([DateTime]::Parse($e.detected_at), [DateTimeKind]::Utc)
                $latency = ($detUtc - $capUtc).TotalSeconds
                $verdict = if ($latency -le 10 -and $latency -ge 0) { "PASS" } else { "WARN" }
                $lcolor = if ($verdict -eq "PASS") { "Green" } else { "Yellow" }
                $latencyR = [Math]::Round($latency, 2)
                Write-Host "    event_id=$($e.event_id) capture=$($cam[0].last_frame_at) -> insert=$($e.detected_at) -> latency ${latencyR}s [$verdict]" -ForegroundColor $lcolor
            } catch {
                Write-Host "    event_id=$($e.event_id) latency parse failed: $($_.Exception.Message)" -ForegroundColor Gray
            }
        }
    }
} else {
    Write-Host "  [!] No new rows. Possible causes:" -ForegroundColor Yellow
    Write-Host "      - No detectable object in camera frame" -ForegroundColor Yellow
    Write-Host "      - fire frames_required=5 not met (try larger -NumCycles)" -ForegroundColor Yellow
    Write-Host "      - RTSP connect failed -> cameras.last_frame_at not updated" -ForegroundColor Yellow
}

# ----------------------------------------------------------------------
# Step 6 - Restore cameras to mp4
# ----------------------------------------------------------------------
Write-Host ""
if (-not $SkipRestore) {
    Write-Host "[Step 6] Restore cameras to mp4" -ForegroundColor Yellow
    foreach ($id in $CameraIds) {
        $orig = $backupMap[$id]
        if ($orig) {
            $restoreBody = ConvertTo-Json @{ live_url_detail = $orig } -Compress
            $null = Invoke-RestMethod -Method Patch -Uri "$URL/rest/v1/cameras?camera_id=eq.$id" -Headers $headersPatch -Body $restoreBody
            Write-Host "  camera_id=$id restored OK" -ForegroundColor Green
        }
    }
    $urlAfter = "$URL/rest/v1/cameras?camera_id=in.($camFilter)" + "&" + "select=camera_id,live_url_detail" + "&" + "order=camera_id"
    $camsAfter = Invoke-RestMethod -Method Get -Uri $urlAfter -Headers $headers
    $stillRtsp = $camsAfter | Where-Object { $_.live_url_detail -match "^rtsp://" }
    if ($stillRtsp.Count -eq 0) {
        Write-Host "  [+] all cameras restored to mp4 OK" -ForegroundColor Green
    } else {
        Write-Host "  [!] some cameras still RTSP - manual fix needed:" -ForegroundColor Red
        $stillRtsp | ForEach-Object { Write-Host "    camera_id=$($_.camera_id) live_url=$($_.live_url_detail)" -ForegroundColor Red }
    }
} else {
    Write-Host "[Step 6] SkipRestore=on - SKIPPED (manual restore needed!)" -ForegroundColor Yellow
    Write-Host "         Restore SQL:" -ForegroundColor Yellow
    foreach ($id in $CameraIds) {
        Write-Host "         UPDATE cameras SET live_url_detail = '$($backupMap[$id])' WHERE camera_id = $id;" -ForegroundColor Yellow
    }
}

# ----------------------------------------------------------------------
# Step 7 - Summary
# ----------------------------------------------------------------------
$totalSec = [Math]::Round(((Get-Date).ToUniversalTime() - $cycleStartUtc).TotalSeconds, 1)
Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Demo done - Phase 8 RTSP-02" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " new detection_events: $newCount rows"
Write-Host " total elapsed       : ${totalSec}s"
Write-Host ""
if ($newCount -gt 0) {
    Write-Host " [+] capture images have bbox + label drawn (scheduler annotate_capture_with_bbox)" -ForegroundColor Green
    Write-Host " [+] demo slide evidence:" -ForegroundColor Green
    $firstId = $newEvents[0].event_id
    $lastId = $newEvents[-1].event_id
    Write-Host "    - event_id $firstId..$lastId (persisted in DB)"
    Write-Host "    - capture URLs public (see Step 5b)"
    Write-Host "    - latency measured (see Step 5c, SC #2 <=10s)"
}
Write-Host ""
