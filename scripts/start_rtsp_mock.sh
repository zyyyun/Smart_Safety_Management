#!/usr/bin/env bash
# scripts/start_rtsp_mock.sh — Phase 8 plan 04 mediamtx launcher
# Windows Git Bash 호환. mediamtx 가 foreground 로 실행되며 5 paths (fire/helmet/forklift/person/fall) 의
# runOnDemand 가 클라이언트 RTSP connect 시 ffmpeg 를 자동 spawn 한다.
# 사용: bash scripts/start_rtsp_mock.sh           # foreground
#       bash scripts/start_rtsp_mock.sh &         # background
# 정리: bash scripts/stop_rtsp_mock.sh (T-8-04 mitigation, SIGKILL — Pitfall 5)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MEDIAMTX_BIN="$ROOT/bin/mediamtx/mediamtx.exe"
CONFIG="$ROOT/scripts/mediamtx.yml"

if [[ ! -x "$MEDIAMTX_BIN" ]]; then
    echo "[FATAL] mediamtx binary not found at $MEDIAMTX_BIN"
    echo "        Download: https://github.com/bluenviron/mediamtx/releases/latest"
    echo "        Extract to: $ROOT/bin/mediamtx/"
    exit 1
fi

if ! command -v ffmpeg >/dev/null 2>&1; then
    echo "[FATAL] ffmpeg not found on PATH (required for runOnDemand publish)"
    echo "        mediamtx will start but all 5 paths will fail to publish."
    exit 1
fi

cd "$ROOT"
echo "[INFO] mediamtx server starting on :8554 (paths: fire, helmet, forklift, person, fall)"
echo "[INFO] reference_media root: $(pwd)/reference_media"
echo "[INFO] press Ctrl-C to stop (or run scripts/stop_rtsp_mock.sh from another shell)"
exec "$MEDIAMTX_BIN" "$CONFIG"
