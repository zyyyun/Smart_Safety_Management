#!/bin/bash
# tests/smoke/ops_create.sh — Phase 12 TBM-08 curl smoke (Plan 12-02)
# Usage: bash tests/smoke/ops_create.sh
#   환경: SUPABASE_URL / SUPABASE_ANON_KEY (.env)
#   사이드이펙트: tbm_templates row 1건 INSERT (is_custom=true, work_type='custom_test_<TS>')
#                 시연 후 ops-toggle 또는 SQL 로 제거 권장
#
# Tests (per 12-02-PLAN.md amended):
#   1. 정상 (200): manager testuser1 가 신규 OPS 추가 — hazards 1개 + controls 1개
#      → HTTP 200 + ok:true + template_id
#   2. hazards 빈 → 400: 누락 시 hazards/controls are required
#   3. controls 빈 → 400: hazards 만 있고 controls 누락
#
# T-12-01 verification: 본 smoke 의 testuser1 은 manager 라 ops-create 가능.
#                       (worker 가 user_id 보내면 403 manager only — 별도 test 가능)
# T-12-03 verification: cleanHazards/cleanControls 가 화이트리스트 (id/text/hazard_id/level) 만 통과.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
[[ -f "$SCRIPT_DIR/.env" ]] || { echo "ERROR: .env 없음 ($SCRIPT_DIR/.env)" >&2; exit 1; }
set -a; source "$SCRIPT_DIR/.env"; set +a
: "${SUPABASE_URL:?SUPABASE_URL not set}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY not set (.env)}"
URL="${SUPABASE_URL%/}/functions/v1/notifications"

PASS=0
TS=$(date +%s)
WORK_TYPE="custom_test_${TS}"

echo "=== Test 1: 정상 (200) — manager 가 새 OPS 추가 work_type=$WORK_TYPE ==="
HTTP=$(curl -sS -o /tmp/ops_create_1.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{
      \"action\":\"ops-create\",
      \"user_id\":\"testuser1\",
      \"work_type\":\"$WORK_TYPE\",
      \"title\":\"Custom test OPS\",
      \"description\":\"smoke insertion\",
      \"hazards\":[{\"id\":\"h1\",\"text\":\"smoke hazard 1\"}],
      \"controls\":[{\"id\":\"c1\",\"hazard_id\":\"h1\",\"level\":\"control\",\"text\":\"smoke control 1\"}],
      \"key_actions\":[{\"id\":\"a1\",\"text\":\"smoke action 1\"}],
      \"checks\":[\"smoke check 1\"],
      \"target_detector\":\"fire\",
      \"is_active\":true
    }")
echo "  HTTP $HTTP"; head -c 500 /tmp/ops_create_1.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200" >&2; exit 2; }
grep -q '"ok":true' /tmp/ops_create_1.json || { echo "EXPECTED ok:true" >&2; exit 3; }
grep -q '"template_id"' /tmp/ops_create_1.json || { echo "EXPECTED template_id" >&2; exit 4; }
TEMPLATE_ID=$(grep -oE '"template_id":[0-9]+' /tmp/ops_create_1.json | head -1 | grep -oE '[0-9]+')
[[ -n "$TEMPLATE_ID" ]] || { echo "EXPECTED numeric template_id" >&2; exit 5; }
PASS=$((PASS+1))

echo "=== Test 2: hazards 빈 → 400 ==="
HTTP=$(curl -sS -o /tmp/ops_create_2.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{
      \"action\":\"ops-create\",
      \"user_id\":\"testuser1\",
      \"work_type\":\"empty_hazards_${TS}\",
      \"title\":\"empty hazards\",
      \"hazards\":[],
      \"controls\":[{\"id\":\"c1\",\"text\":\"only control\"}]
    }")
echo "  HTTP $HTTP"; head -c 500 /tmp/ops_create_2.json; echo
[[ "$HTTP" == "400" ]] || { echo "EXPECTED 400" >&2; exit 6; }
grep -qE 'hazards' /tmp/ops_create_2.json || { echo "EXPECTED hazards required message" >&2; exit 7; }
PASS=$((PASS+1))

echo "=== Test 3: controls 빈 → 400 ==="
HTTP=$(curl -sS -o /tmp/ops_create_3.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{
      \"action\":\"ops-create\",
      \"user_id\":\"testuser1\",
      \"work_type\":\"empty_controls_${TS}\",
      \"title\":\"empty controls\",
      \"hazards\":[{\"id\":\"h1\",\"text\":\"some hazard\"}],
      \"controls\":[]
    }")
echo "  HTTP $HTTP"; head -c 500 /tmp/ops_create_3.json; echo
[[ "$HTTP" == "400" ]] || { echo "EXPECTED 400" >&2; exit 8; }
grep -qE 'controls' /tmp/ops_create_3.json || { echo "EXPECTED controls required message" >&2; exit 9; }
PASS=$((PASS+1))

echo "=== ops-create: ${PASS}/3 PASS ==="
echo "CREATED_TEMPLATE_ID=$TEMPLATE_ID"
echo "  (보존됨 — tests/smoke/ops_update.sh + ops_toggle.sh 에 전달 가능, 또는 SQL 로 삭제)"
