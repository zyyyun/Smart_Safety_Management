#!/bin/bash
# tests/smoke/camera_recovered.sh — Phase 8 RTSP-03 회복 알림 curl smoke
# Usage: bash tests/smoke/camera_recovered.sh
#
# Tests (per 08-03-PLAN.md task 1 step 5):
#   1. 정상: camera_id=1, group_id=1
#      → HTTP 200 + body 에 "sent" key 포함 (sent 값은 manager 상태에 의존)
#
# D-09 알림 전이 원칙: down→ok 전이 시점에 cron 함수가 1회 호출. push-only,
# notifications insert 0 (회귀 가드 — SUMMARY 에서 검증).

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
[[ -f "$SCRIPT_DIR/.env" ]] || { echo "ERROR: .env 없음 ($SCRIPT_DIR/.env)" >&2; exit 1; }
set -a; source "$SCRIPT_DIR/.env"; set +a
: "${SUPABASE_URL:?SUPABASE_URL not set}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY not set (.env)}"
URL="${SUPABASE_URL%/}/functions/v1/notifications"

PASS=0

echo "=== Test 1: 정상 호출 (camera_id=1, group_id=1) ==="
HTTP=$(curl -sS -o /tmp/cr1.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d '{"action":"camera-recovered","camera_id":1,"group_id":1}')
echo "  HTTP $HTTP"; head -c 500 /tmp/cr1.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200" >&2; exit 2; }
grep -q '"ok":true' /tmp/cr1.json || { echo "EXPECTED ok:true" >&2; exit 3; }
grep -q '"sent"' /tmp/cr1.json || { echo "EXPECTED sent field" >&2; exit 4; }
PASS=$((PASS+1))

echo "=== camera_recovered: ${PASS}/1 PASS ==="
