#!/bin/bash
# tests/smoke/ops_update.sh — Phase 12 TBM-08 curl smoke (Plan 12-02)
# Usage: TEMPLATE_ID=<N> bash tests/smoke/ops_update.sh
#   환경: SUPABASE_URL / SUPABASE_ANON_KEY (.env)
#         TEMPLATE_ID — ops_create.sh 가 emit 한 template_id, 또는 PostgREST GET 으로 확인
#
# Tests (per 12-02-PLAN.md amended):
#   1. 정상 (200): title + hazards/controls 부분 patch
#   2. hazards 빈 → 400
#   3. 존재하지 않는 template_id → 404
#
# T-12-01 verification: manager 권한 검증

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
[[ -f "$SCRIPT_DIR/.env" ]] || { echo "ERROR: .env 없음 ($SCRIPT_DIR/.env)" >&2; exit 1; }
set -a; source "$SCRIPT_DIR/.env"; set +a
: "${SUPABASE_URL:?SUPABASE_URL not set}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY not set (.env)}"
: "${TEMPLATE_ID:?TEMPLATE_ID env required (run ops_create.sh first or read from tbm_templates)}"
URL="${SUPABASE_URL%/}/functions/v1/notifications"

PASS=0

echo "=== Test 1: 정상 (200) — template_id=$TEMPLATE_ID title + hazards/controls 갱신 ==="
HTTP=$(curl -sS -o /tmp/ops_update_1.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{
      \"action\":\"ops-update\",
      \"user_id\":\"testuser1\",
      \"template_id\":$TEMPLATE_ID,
      \"title\":\"Updated OPS smoke\",
      \"hazards\":[{\"id\":\"h1\",\"text\":\"updated hazard 1\"},{\"id\":\"h2\",\"text\":\"new hazard 2\"}],
      \"controls\":[{\"id\":\"c1\",\"hazard_id\":\"h1\",\"level\":\"control\",\"text\":\"updated control 1\"}]
    }")
echo "  HTTP $HTTP"; head -c 500 /tmp/ops_update_1.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200" >&2; exit 2; }
grep -q '"ok":true' /tmp/ops_update_1.json || { echo "EXPECTED ok:true" >&2; exit 3; }
PASS=$((PASS+1))

echo "=== Test 2: hazards 빈 → 400 ==="
HTTP=$(curl -sS -o /tmp/ops_update_2.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{
      \"action\":\"ops-update\",
      \"user_id\":\"testuser1\",
      \"template_id\":$TEMPLATE_ID,
      \"hazards\":[]
    }")
echo "  HTTP $HTTP"; head -c 500 /tmp/ops_update_2.json; echo
[[ "$HTTP" == "400" ]] || { echo "EXPECTED 400" >&2; exit 4; }
grep -qE 'hazards' /tmp/ops_update_2.json || { echo "EXPECTED hazards required" >&2; exit 5; }
PASS=$((PASS+1))

echo "=== Test 3: 존재하지 않는 template_id → 404 ==="
BAD_ID=$((TEMPLATE_ID + 9999))
HTTP=$(curl -sS -o /tmp/ops_update_3.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{
      \"action\":\"ops-update\",
      \"user_id\":\"testuser1\",
      \"template_id\":$BAD_ID,
      \"title\":\"should not exist\"
    }")
echo "  HTTP $HTTP"; head -c 500 /tmp/ops_update_3.json; echo
[[ "$HTTP" == "404" ]] || { echo "EXPECTED 404" >&2; exit 6; }
grep -qE 'not found' /tmp/ops_update_3.json || { echo "EXPECTED not found message" >&2; exit 7; }
PASS=$((PASS+1))

echo "=== ops-update: ${PASS}/3 PASS ==="
