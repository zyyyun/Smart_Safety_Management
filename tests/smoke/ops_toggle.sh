#!/bin/bash
# tests/smoke/ops_toggle.sh — Phase 12 TBM-08 curl smoke (Plan 12-02)
# Usage: TEMPLATE_ID=<N> bash tests/smoke/ops_toggle.sh
#   환경: SUPABASE_URL / SUPABASE_ANON_KEY (.env)
#         TEMPLATE_ID — ops_create.sh 가 emit, 또는 PostgREST GET 으로 확인
#
# Tests (per 12-02-PLAN.md amended):
#   1. 비활성화 (200): is_active=false
#   2. 재활성화 (200): is_active=true
#   3. template_id 누락 → 400
#
# T-12-01 verification: manager 권한 검증 (Edge Function 의 requireManager guard)

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
[[ -f "$SCRIPT_DIR/.env" ]] || { echo "ERROR: .env 없음 ($SCRIPT_DIR/.env)" >&2; exit 1; }
set -a; source "$SCRIPT_DIR/.env"; set +a
: "${SUPABASE_URL:?SUPABASE_URL not set}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY not set (.env)}"
: "${TEMPLATE_ID:?TEMPLATE_ID env required (run ops_create.sh first)}"
URL="${SUPABASE_URL%/}/functions/v1/notifications"

PASS=0

echo "=== Test 1: 비활성화 (200) — template_id=$TEMPLATE_ID is_active=false ==="
HTTP=$(curl -sS -o /tmp/ops_toggle_1.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{
      \"action\":\"ops-toggle\",
      \"user_id\":\"testuser1\",
      \"template_id\":$TEMPLATE_ID,
      \"is_active\":false
    }")
echo "  HTTP $HTTP"; head -c 500 /tmp/ops_toggle_1.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200" >&2; exit 2; }
grep -q '"ok":true' /tmp/ops_toggle_1.json || { echo "EXPECTED ok:true" >&2; exit 3; }
grep -q '"is_active":false' /tmp/ops_toggle_1.json || { echo "EXPECTED is_active:false" >&2; exit 4; }
PASS=$((PASS+1))

echo "=== Test 2: 재활성화 (200) — is_active=true ==="
HTTP=$(curl -sS -o /tmp/ops_toggle_2.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{
      \"action\":\"ops-toggle\",
      \"user_id\":\"testuser1\",
      \"template_id\":$TEMPLATE_ID,
      \"is_active\":true
    }")
echo "  HTTP $HTTP"; head -c 500 /tmp/ops_toggle_2.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200" >&2; exit 5; }
grep -q '"is_active":true' /tmp/ops_toggle_2.json || { echo "EXPECTED is_active:true" >&2; exit 6; }
PASS=$((PASS+1))

echo "=== Test 3: template_id 누락 → 400 ==="
HTTP=$(curl -sS -o /tmp/ops_toggle_3.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{
      \"action\":\"ops-toggle\",
      \"user_id\":\"testuser1\",
      \"is_active\":false
    }")
echo "  HTTP $HTTP"; head -c 500 /tmp/ops_toggle_3.json; echo
[[ "$HTTP" == "400" ]] || { echo "EXPECTED 400" >&2; exit 7; }
grep -qE 'template_id' /tmp/ops_toggle_3.json || { echo "EXPECTED template_id required" >&2; exit 8; }
PASS=$((PASS+1))

echo "=== ops-toggle: ${PASS}/3 PASS ==="
