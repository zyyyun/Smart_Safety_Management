#!/bin/bash
# tests/smoke/tbm_end.sh — Phase 9 TBM-02 curl smoke (Plan 09-02 Task 2)
# Usage: SESSION_ID=<N> bash tests/smoke/tbm_end.sh
#   환경: SUPABASE_URL / SUPABASE_ANON_KEY (.env), SESSION_ID (orchestrator)
#   사이드이펙트: tbm_sessions.ended_at = now() UPDATE (scenario 1)
#   주의: scenario 1 이 session 을 종료시키므로 가장 마지막에 호출
#
# Tests (per 09-02-PLAN.md Task 2 step 4):
#   1. leader 불일치 → 404: SESSION_ID + leader_user_id=testuser_w1 (worker, not leader)
#      → HTTP 404 + "not led by user" message
#   2. payload 누락 → 400: leader_user_id 생략
#      → HTTP 400 + required-fields message
#   3. 정상: SESSION_ID + leader_user_id=testuser1
#      → HTTP 200 + ok:true + ended_at + participant_count
#
# T-9-04 검증: scenario 1 의 404 = .eq("leader_user_id", ...) + .is("ended_at", null) 미매치.
# 주의: scenario 순서는 1(불일치) → 2(누락) → 3(정상) — scenario 3 이 마지막이라 session 종료 OK.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
[[ -f "$SCRIPT_DIR/.env" ]] || { echo "ERROR: .env 없음 ($SCRIPT_DIR/.env)" >&2; exit 1; }
set -a; source "$SCRIPT_DIR/.env"; set +a
: "${SUPABASE_URL:?SUPABASE_URL not set}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY not set (.env)}"
: "${SESSION_ID:?SESSION_ID env required (use orchestrator or pre-export)}"
URL="${SUPABASE_URL%/}/functions/v1/notifications"

PASS=0

echo "=== Test 1: leader 불일치 → 404 (leader_user_id=testuser_w1, worker not leader) ==="
HTTP=$(curl -sS -o /tmp/tbm_end_1.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"tbm-end\",\"session_id\":$SESSION_ID,\"leader_user_id\":\"testuser_w1\"}")
echo "  HTTP $HTTP"; head -c 500 /tmp/tbm_end_1.json; echo
[[ "$HTTP" == "404" ]] || { echo "EXPECTED 404" >&2; exit 2; }
grep -q 'not led by user' /tmp/tbm_end_1.json || { echo "EXPECTED 'not led by user' message" >&2; exit 3; }
PASS=$((PASS+1))

echo "=== Test 2: payload 누락 → 400 (leader_user_id 생략) ==="
HTTP=$(curl -sS -o /tmp/tbm_end_2.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"tbm-end\",\"session_id\":$SESSION_ID}")
echo "  HTTP $HTTP"; head -c 500 /tmp/tbm_end_2.json; echo
[[ "$HTTP" == "400" ]] || { echo "EXPECTED 400" >&2; exit 4; }
grep -q 'required' /tmp/tbm_end_2.json || { echo "EXPECTED required-fields message" >&2; exit 5; }
PASS=$((PASS+1))

echo "=== Test 3: 정상 (leader_user_id=testuser1) ==="
HTTP=$(curl -sS -o /tmp/tbm_end_3.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"tbm-end\",\"session_id\":$SESSION_ID,\"leader_user_id\":\"testuser1\",\"key_hazard_id\":\"h1\",\"feedback_notes\":\"smoke test complete\"}")
echo "  HTTP $HTTP"; head -c 500 /tmp/tbm_end_3.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200" >&2; exit 6; }
grep -q '"ok":true' /tmp/tbm_end_3.json || { echo "EXPECTED ok:true" >&2; exit 7; }
grep -q '"ended_at"' /tmp/tbm_end_3.json || { echo "EXPECTED ended_at field" >&2; exit 8; }
grep -q '"participant_count"' /tmp/tbm_end_3.json || { echo "EXPECTED participant_count field" >&2; exit 9; }
PASS=$((PASS+1))

echo "=== tbm-end: ${PASS}/3 PASS ==="
