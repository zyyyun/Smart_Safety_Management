#!/bin/bash
# tests/smoke/watch_pair.sh — Phase 7 BRIDGE-03 curl smoke
#
# Tests:
#   1. pair 정상 (testuser1 + 21:02:02:06:01:69) → HTTP 200 + ok:true
#   2. invalid MAC format → HTTP 400 + "invalid MAC"
#   3. spoofing (다른 user_id 같은 mac) → HTTP 409 + "already paired"
#   4. unpair (testuser1) → HTTP 200
#   5. re-pair idempotent (testuser1 재등록) → HTTP 200
#
# Per 07-02 PLAN.md / 07-CONTEXT.md D-04b (T-7-03 spoofing mitigation).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
[[ -f "$SCRIPT_DIR/.env" ]] || { echo "ERROR: .env 없음 ($SCRIPT_DIR/.env)" >&2; exit 1; }
set -a; source "$SCRIPT_DIR/.env"; set +a
: "${SUPABASE_URL:?SUPABASE_URL not set}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY not set}"
USER_ID="${TEST_USER_ID:-testuser1}"
MAC="21:02:02:06:01:69"
URL="${SUPABASE_URL%/}/functions/v1/notifications"

echo "=== Test 1: pair 정상 ==="
HTTP=$(curl -sS -o /tmp/wp1.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"watch-pair\",\"user_id\":\"$USER_ID\",\"mac_address\":\"$MAC\",\"op\":\"pair\"}")
echo "  HTTP $HTTP"; head -c 500 /tmp/wp1.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200" >&2; exit 2; }
grep -q '"ok":true' /tmp/wp1.json || { echo "EXPECTED ok:true" >&2; exit 3; }

echo "=== Test 2: invalid MAC format (expect 400) ==="
HTTP=$(curl -sS -o /tmp/wp2.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"watch-pair\",\"user_id\":\"$USER_ID\",\"mac_address\":\"not-a-mac\",\"op\":\"pair\"}")
echo "  HTTP $HTTP"; head -c 300 /tmp/wp2.json; echo
[[ "$HTTP" == "400" ]] || { echo "EXPECTED 400 invalid MAC" >&2; exit 4; }
grep -q 'invalid MAC' /tmp/wp2.json || { echo "EXPECTED invalid MAC msg" >&2; exit 5; }

echo "=== Test 3: spoofing (다른 user 같은 mac) expect 409 ==="
HTTP=$(curl -sS -o /tmp/wp3.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"watch-pair\",\"user_id\":\"someone_else\",\"mac_address\":\"$MAC\",\"op\":\"pair\"}")
echo "  HTTP $HTTP"; head -c 300 /tmp/wp3.json; echo
[[ "$HTTP" == "409" ]] || { echo "EXPECTED 409 spoofing block" >&2; exit 6; }
grep -q 'already paired' /tmp/wp3.json || { echo "EXPECTED already-paired" >&2; exit 7; }

echo "=== Test 4: unpair ==="
HTTP=$(curl -sS -o /tmp/wp4.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"watch-pair\",\"user_id\":\"$USER_ID\",\"op\":\"unpair\"}")
echo "  HTTP $HTTP"; head -c 300 /tmp/wp4.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200 unpair" >&2; exit 8; }

echo "=== Test 5: re-pair idempotent ==="
HTTP=$(curl -sS -o /tmp/wp5.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"watch-pair\",\"user_id\":\"$USER_ID\",\"mac_address\":\"$MAC\",\"op\":\"pair\"}")
echo "  HTTP $HTTP"; head -c 300 /tmp/wp5.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200 re-pair" >&2; exit 9; }

echo "=== ALL WATCH-PAIR SMOKE TESTS PASSED ==="
