#!/bin/bash
# tests/smoke/camera_down.sh — Phase 8 RTSP-03 curl smoke
# Usage: bash tests/smoke/camera_down.sh
#
# Tests (per 08-03-PLAN.md task 1 step 4 / 08-RESEARCH §Example D):
#   1. 정상: camera_id=1, group_id=1, last_frame_at=2026-05-15T12:00:00Z
#      → HTTP 200 + body 에 "sent" key 포함 (sent 값은 manager+fcm_token 상태에 의존 — 0 도 OK)
#   2. payload 누락: camera_id 없음 → HTTP 400
#   3. no-manager: group_id=99999 → HTTP 200 + "sent":0 (early-return 검증)
#
# D-09 회귀 가드: 본 smoke 후 notifications row 증가 0 (push-only — SUMMARY 에서 검증).
# C4 정정 (RESEARCH 정정 #4 / Pitfall 3): Edge Function payload 에 channel_id 명시 X.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
[[ -f "$SCRIPT_DIR/.env" ]] || { echo "ERROR: .env 없음 ($SCRIPT_DIR/.env)" >&2; exit 1; }
set -a; source "$SCRIPT_DIR/.env"; set +a
: "${SUPABASE_URL:?SUPABASE_URL not set}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY not set (.env)}"
URL="${SUPABASE_URL%/}/functions/v1/notifications"

PASS=0

echo "=== Test 1: 정상 호출 (camera_id=1, group_id=1) ==="
HTTP=$(curl -sS -o /tmp/cd1.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d '{"action":"camera-down","camera_id":1,"group_id":1,"last_frame_at":"2026-05-15T12:00:00Z"}')
echo "  HTTP $HTTP"; head -c 500 /tmp/cd1.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200" >&2; exit 2; }
grep -q '"ok":true' /tmp/cd1.json || { echo "EXPECTED ok:true" >&2; exit 3; }
grep -q '"sent"' /tmp/cd1.json || { echo "EXPECTED sent field" >&2; exit 4; }
PASS=$((PASS+1))

echo "=== Test 2: payload 누락 (camera_id 없음) ==="
HTTP=$(curl -sS -o /tmp/cd2.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d '{"action":"camera-down"}')
echo "  HTTP $HTTP"; head -c 500 /tmp/cd2.json; echo
[[ "$HTTP" == "400" ]] || { echo "EXPECTED 400" >&2; exit 5; }
grep -q 'camera_id and group_id' /tmp/cd2.json || { echo "EXPECTED required-fields msg" >&2; exit 6; }
PASS=$((PASS+1))

echo "=== Test 3: no-manager (group_id=99999) ==="
HTTP=$(curl -sS -o /tmp/cd3.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d '{"action":"camera-down","camera_id":1,"group_id":99999,"last_frame_at":"2026-05-15T12:00:00Z"}')
echo "  HTTP $HTTP"; head -c 500 /tmp/cd3.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200" >&2; exit 7; }
grep -q '"sent":0' /tmp/cd3.json || { echo "EXPECTED sent:0 (no managers)" >&2; exit 8; }
grep -q 'no managers in group' /tmp/cd3.json || { echo "EXPECTED no-managers reason" >&2; exit 9; }
PASS=$((PASS+1))

echo "=== camera_down: ${PASS}/3 PASS ==="
