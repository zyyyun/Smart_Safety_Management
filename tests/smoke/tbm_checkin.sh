#!/bin/bash
# tests/smoke/tbm_checkin.sh — Phase 9 TBM-02 curl smoke (Plan 09-02 Task 2)
# Usage: SESSION_ID=<N> bash tests/smoke/tbm_checkin.sh
#   환경: SUPABASE_URL / SUPABASE_ANON_KEY (.env), SESSION_ID (orchestrator 또는 직접)
#   사이드이펙트: tbm_participants row INSERT (testuser_w1 — UNIQUE 충돌 시 idempotent)
#
# Tests (per 09-02-PLAN.md Task 2 step 3):
#   1. 정상: SESSION_ID + user_id=testuser_w1 (group_id=1 worker)
#      → HTTP 200 + ok:true + participant_id + signed_at
#   2. ownership 위반 → 403: user_id=younseu (group_id=2 manager — cross-group)
#      → HTTP 403 + "user not in session group"
#   3. idempotent → 200: scenario 1 재호출
#      → HTTP 200 + idempotent:true
#
# T-9-03 검증: scenario 2 의 403 = profile.group_id !== session.group_id 차단.
# Pitfall 5 검증: scenario 3 의 200 idempotent = 23505 catch path.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
[[ -f "$SCRIPT_DIR/.env" ]] || { echo "ERROR: .env 없음 ($SCRIPT_DIR/.env)" >&2; exit 1; }
set -a; source "$SCRIPT_DIR/.env"; set +a
: "${SUPABASE_URL:?SUPABASE_URL not set}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY not set (.env)}"
: "${SESSION_ID:?SESSION_ID env required (use orchestrator or pre-export)}"
URL="${SUPABASE_URL%/}/functions/v1/notifications"

PASS=0

echo "=== Test 1: 정상 (SESSION_ID=$SESSION_ID, user_id=testuser_w1) ==="
HTTP=$(curl -sS -o /tmp/tbm_checkin_1.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"tbm-checkin\",\"session_id\":$SESSION_ID,\"user_id\":\"testuser_w1\"}")
echo "  HTTP $HTTP"; head -c 500 /tmp/tbm_checkin_1.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200" >&2; exit 2; }
grep -q '"ok":true' /tmp/tbm_checkin_1.json || { echo "EXPECTED ok:true" >&2; exit 3; }
grep -q '"participant_id"' /tmp/tbm_checkin_1.json || { echo "EXPECTED participant_id field" >&2; exit 4; }
grep -q '"signed_at"' /tmp/tbm_checkin_1.json || { echo "EXPECTED signed_at field" >&2; exit 5; }
PASS=$((PASS+1))

echo "=== Test 2: ownership 위반 → 403 (user_id=younseu, group_id=2 cross-group) ==="
HTTP=$(curl -sS -o /tmp/tbm_checkin_2.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"tbm-checkin\",\"session_id\":$SESSION_ID,\"user_id\":\"younseu\"}")
echo "  HTTP $HTTP"; head -c 500 /tmp/tbm_checkin_2.json; echo
[[ "$HTTP" == "403" ]] || { echo "EXPECTED 403" >&2; exit 6; }
grep -q 'user not in session group' /tmp/tbm_checkin_2.json || { echo "EXPECTED ownership-fail message" >&2; exit 7; }
PASS=$((PASS+1))

echo "=== Test 3: idempotent → 200 (재호출, testuser_w1) ==="
HTTP=$(curl -sS -o /tmp/tbm_checkin_3.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"tbm-checkin\",\"session_id\":$SESSION_ID,\"user_id\":\"testuser_w1\"}")
echo "  HTTP $HTTP"; head -c 500 /tmp/tbm_checkin_3.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200" >&2; exit 8; }
grep -q '"idempotent":true' /tmp/tbm_checkin_3.json || { echo "EXPECTED idempotent:true" >&2; exit 9; }
PASS=$((PASS+1))

echo "=== tbm-checkin: ${PASS}/3 PASS ==="
