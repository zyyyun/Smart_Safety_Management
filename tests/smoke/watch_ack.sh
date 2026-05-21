#!/bin/bash
# tests/smoke/watch_ack.sh — Phase 7 BRIDGE-02b curl smoke
# Usage: bash tests/smoke/watch_ack.sh <alert_id>
#
# Tests:
#   1. 정상 ack (alert_id, testuser1) → HTTP 200 + ok:true + ack_at
#   2. idempotency (같은 alert 재호출) → HTTP 404 + already acknowledged / not found
#   3. ownership (다른 user_id) → HTTP 404 (subquery 0행 또는 already acked)
#
# Per 07-02 PLAN.md / 07-CONTEXT.md D-03 (T-7-02 ownership mitigation, idempotency 가드).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
[[ -f "$SCRIPT_DIR/.env" ]] || { echo "ERROR: .env 없음 ($SCRIPT_DIR/.env)" >&2; exit 1; }
set -a; source "$SCRIPT_DIR/.env"; set +a
: "${SUPABASE_URL:?SUPABASE_URL not set}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY not set (07-01 BuildConfig + .env)}"
ALERT_ID="${1:-1}"
USER_ID="${TEST_USER_ID:-testuser1}"
URL="${SUPABASE_URL%/}/functions/v1/notifications"

echo "=== Test 1: 정상 ack (alert_id=$ALERT_ID, user_id=$USER_ID) ==="
HTTP=$(curl -sS -o /tmp/wa1.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"watch-ack\",\"alert_id\":$ALERT_ID,\"user_id\":\"$USER_ID\"}")
echo "  HTTP $HTTP"; head -c 500 /tmp/wa1.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200" >&2; exit 2; }
grep -q '"ok":true' /tmp/wa1.json || { echo "EXPECTED ok:true" >&2; exit 3; }
grep -q '"ack_at"' /tmp/wa1.json || { echo "EXPECTED ack_at field" >&2; exit 4; }

echo "=== Test 2: idempotency (same alert 두번째 ack expect 404) ==="
HTTP=$(curl -sS -o /tmp/wa2.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"watch-ack\",\"alert_id\":$ALERT_ID,\"user_id\":\"$USER_ID\"}")
echo "  HTTP $HTTP"; head -c 500 /tmp/wa2.json; echo
[[ "$HTTP" == "404" ]] || { echo "EXPECTED 404 already-acked" >&2; exit 5; }
grep -qE 'already acknowledged|not found' /tmp/wa2.json || { echo "EXPECTED already-acked msg" >&2; exit 6; }

echo "=== Test 3: ownership (다른 user_id expect 404) ==="
HTTP=$(curl -sS -o /tmp/wa3.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"watch-ack\",\"alert_id\":$ALERT_ID,\"user_id\":\"someone_else\"}")
echo "  HTTP $HTTP"; head -c 500 /tmp/wa3.json; echo
[[ "$HTTP" == "404" ]] || { echo "EXPECTED 404 ownership" >&2; exit 7; }

echo "=== ALL WATCH-ACK SMOKE TESTS PASSED ==="
