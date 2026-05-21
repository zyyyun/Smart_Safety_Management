# =============================================================================
# J2208A watch real device demo automation script (2026-05-21)
# =============================================================================
# Double-click or run from PowerShell:
#   .\scripts\demo_j2208a_real_device.ps1
#
# Steps:
#   1. Auto-load ai_agent/.env (SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY)
#   2. Set WATCH_OWNER_USER_ID env (default testuser1, 010 seed match)
#   3. Run scripts/j2208a_sensor_reader.py
#      - On BLE connect success -> auto UPSERT devices row (pair-safe)
#      - Then raw_events / wear_state / safety_alerts pipeline as usual
#   4. App auto-detects via HomeActivity SELECT devices (no manual PairWatchSection)
#
# Options:
#   -UserId       : devices.user_id to pair (default 'testuser1')
#   -MacAddress   : BLE MAC, e.g. '21:02:02:06:01:69'. Empty = auto-scan.
#   -ScanOnly     : scan-only (no connect, no upsert). For diagnostics.
#
# Examples:
#   .\scripts\demo_j2208a_real_device.ps1
#   .\scripts\demo_j2208a_real_device.ps1 -MacAddress 21:02:02:06:01:69
#   .\scripts\demo_j2208a_real_device.ps1 -UserId siteA_manager
#   .\scripts\demo_j2208a_real_device.ps1 -ScanOnly
#
# Pre-flight (user):
#   1. JCWear phone app CLOSED (BLE master conflict otherwise)
#   2. Watch turned on, within ~5m of PC
#   3. PC BLE adapter enabled
# =============================================================================

[CmdletBinding()]
param(
    [string]$UserId = "testuser1",
    [string]$MacAddress = "",
    [switch]$ScanOnly
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " J2208A Watch Real Device Demo (auto-register)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host " UserId        : $UserId"
Write-Host " MacAddress    : $(if ($MacAddress) { $MacAddress } else { '(auto-scan)' })"
Write-Host " ScanOnly      : $ScanOnly"
Write-Host ""

# ----------------------------------------------------------------------
# Step 0 - Load env from ai_agent/.env (SUPABASE_URL + SERVICE_ROLE_KEY)
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
Write-Host "[+] env loaded (SUPABASE_URL=$($URL.Substring(0, [Math]::Min(40, $URL.Length)))...)"

# ----------------------------------------------------------------------
# Step 1 - Set WATCH_OWNER_USER_ID for j2208a_sensor_reader.py
# ----------------------------------------------------------------------
$env:WATCH_OWNER_USER_ID = $UserId
$env:PYTHONUNBUFFERED = "1"  # live stdout streaming
Write-Host "[+] WATCH_OWNER_USER_ID = $UserId"

# ----------------------------------------------------------------------
# Step 2 - Launch j2208a_sensor_reader.py
# ----------------------------------------------------------------------
$python = "C:\Users\ANNA\miniconda3\python.exe"
$readerPy = Join-Path $RepoRoot "scripts\j2208a_sensor_reader.py"
if (-not (Test-Path $readerPy)) {
    Write-Host "[!] $readerPy not found." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[Launch] python scripts/j2208a_sensor_reader.py" -ForegroundColor Yellow
Write-Host "  (Ctrl+C to stop)"
Write-Host ""

if ($ScanOnly) {
    & $python -u $readerPy --scan
} elseif ($MacAddress) {
    & $python -u $readerPy $MacAddress
} else {
    & $python -u $readerPy
}
