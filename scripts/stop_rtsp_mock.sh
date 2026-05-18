#!/usr/bin/env bash
# scripts/stop_rtsp_mock.sh — Phase 8 plan 04 cleanup (T-8-04 mitigation)
# mediamtx + spawned ffmpeg 모두 강제 종료 (SIGKILL / taskkill /F).
# Pitfall 5: graceful shutdown 시 RTSP TEARDOWN 으로 capture_rtsp backoff 가 즉시 EOF 반환 → SnapshotError after 3 attempts 패턴 검증 불가.
# 따라서 taskkill /F 또는 pkill -9 로 abrupt 종료.
set -uo pipefail

echo "[INFO] killing mediamtx + spawned ffmpeg publishers (T-8-04 mitigation, SIGKILL)"

# Windows Git Bash — taskkill 우선
if command -v taskkill >/dev/null 2>&1; then
    taskkill //F //IM mediamtx.exe 2>/dev/null && echo "[OK] mediamtx.exe killed" || echo "[INFO] mediamtx.exe not running"
    taskkill //F //IM ffmpeg.exe   2>/dev/null && echo "[OK] ffmpeg.exe killed"   || echo "[INFO] ffmpeg.exe not running"
else
    # Linux/Mac fallback
    pkill -9 -f mediamtx 2>/dev/null && echo "[OK] mediamtx killed" || echo "[INFO] mediamtx not running"
    pkill -9 -f 'ffmpeg.*rtsp://localhost:8554' 2>/dev/null && echo "[OK] ffmpeg killed" || echo "[INFO] ffmpeg not running"
fi

# 검증 — process 0건이어야 함
sleep 1
if command -v tasklist >/dev/null 2>&1; then
    remaining=$(tasklist //FI "IMAGENAME eq mediamtx.exe" 2>/dev/null | grep -c mediamtx.exe || true)
    if [[ "$remaining" -gt 0 ]]; then
        echo "[WARN] mediamtx.exe still running after kill — manual cleanup may be required"
    fi
fi
echo "[INFO] cleanup complete"
