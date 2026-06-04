#!/bin/bash
# tests/smoke/tbm_start.sh — Phase 9 TBM-03 curl smoke (Plan 09-02 Task 2)
# Usage: bash tests/smoke/tbm_start.sh
#   환경: SUPABASE_URL / SUPABASE_ANON_KEY (.env)
#   사이드이펙트: tbm_sessions row 1건 INSERT (오늘 group_id=1)
#                 tbm_checklists rows INSERT (forklift work_type)
#   출력: SESSION_ID 를 stdout 마지막 line 에 'SESSION_ID=<N>' 형식으로 emit
#         (orchestrator 가 후속 smoke 에 전달)
#
# Tests (per 09-02-PLAN.md Task 2 step 2):
#   1. normal: leader=testuser1, group_id=1, work_type=forklift, work_scope="Forklift bay A"
#      -> HTTP 200 + ok:true + session_id + checklist_count
#   2. UNIQUE 23505 → 409: 같은 day 두 번째 호출
#      → HTTP 409 + "이미 오늘 세션" message
#   3. payload 누락 → 400: expected_end_at 생략
#      → HTTP 400 + required-fields message
#
# D-09 회귀 가드: 본 smoke 후 notifications row 증가 0 (push-only — orchestrator 가 검증).
# T-9-02 검증: scenario 2 의 409 응답이 DB-level UNIQUE 충돌 catch 정상 동작.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
[[ -f "$SCRIPT_DIR/.env" ]] || { echo "ERROR: .env 없음 ($SCRIPT_DIR/.env)" >&2; exit 1; }
set -a; source "$SCRIPT_DIR/.env"; set +a
: "${SUPABASE_URL:?SUPABASE_URL not set}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY not set (.env)}"
URL="${SUPABASE_URL%/}/functions/v1/notifications"

# expected_end_at = now + 15min (ISO8601 with offset — Phase 4 010 UTC immutability)
NOW_PLUS_15=$(date -u -d '+15 minutes' +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null \
              || python -c "from datetime import datetime, timedelta, timezone; print((datetime.now(timezone.utc)+timedelta(minutes=15)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
echo "  prep: expected_end_at=$NOW_PLUS_15"

PASS=0
SESSION_ID=""
WORK_SCOPE="Forklift bay A"

echo "=== Test 1: normal (leader=testuser1, group_id=1, work_type=forklift, work_scope=$WORK_SCOPE) ==="
HTTP=$(curl -sS -o /tmp/tbm_start_1.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"tbm-start\",\"leader_user_id\":\"testuser1\",\"group_id\":1,\"work_type\":\"forklift\",\"work_scope\":\"$WORK_SCOPE\",\"expected_end_at\":\"$NOW_PLUS_15\"}")
echo "  HTTP $HTTP"; head -c 500 /tmp/tbm_start_1.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200" >&2; exit 2; }
grep -q '"ok":true' /tmp/tbm_start_1.json || { echo "EXPECTED ok:true" >&2; exit 3; }
grep -q '"checklist_count"' /tmp/tbm_start_1.json || { echo "EXPECTED checklist_count" >&2; exit 4; }
# extract session_id (numeric value)
SESSION_ID=$(grep -oE '"session_id":[0-9]+' /tmp/tbm_start_1.json | head -1 | grep -oE '[0-9]+')
[[ -n "$SESSION_ID" ]] || { echo "EXPECTED session_id field" >&2; exit 5; }
echo "  -> SESSION_ID=$SESSION_ID"
PASS=$((PASS+1))

echo "=== Test 2: UNIQUE 23505 → 409 (재호출 같은 day) ==="
HTTP=$(curl -sS -o /tmp/tbm_start_2.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"tbm-start\",\"leader_user_id\":\"testuser1\",\"group_id\":1,\"work_type\":\"forklift\",\"work_scope\":\"$WORK_SCOPE\",\"expected_end_at\":\"$NOW_PLUS_15\"}")
echo "  HTTP $HTTP"; head -c 500 /tmp/tbm_start_2.json; echo
[[ "$HTTP" == "409" ]] || { echo "EXPECTED 409" >&2; exit 6; }
grep -q 'already has' /tmp/tbm_start_2.json || { echo "EXPECTED duplicate-session message" >&2; exit 7; }
PASS=$((PASS+1))

echo "=== Test 3: 다른 work_scope → 200 (Amendment 2026-05-26 P1 3-튜플 UNIQUE 검증) ==="
SCOPE_B="Forklift bay B"
HTTP=$(curl -sS -o /tmp/tbm_start_3b.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"tbm-start\",\"leader_user_id\":\"testuser1\",\"group_id\":1,\"work_type\":\"forklift\",\"work_scope\":\"$SCOPE_B\",\"expected_end_at\":\"$NOW_PLUS_15\"}")
echo "  HTTP $HTTP"; head -c 500 /tmp/tbm_start_3b.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200 — different work_scope must succeed on same day" >&2; exit 8; }
grep -q '"ok":true' /tmp/tbm_start_3b.json || { echo "EXPECTED ok:true" >&2; exit 9; }
PASS=$((PASS+1))

echo "=== Test 4: payload 누락 → 400 (expected_end_at 생략) ==="
HTTP=$(curl -sS -o /tmp/tbm_start_4.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d '{"action":"tbm-start","leader_user_id":"testuser1","group_id":1,"work_type":"forklift"}')
echo "  HTTP $HTTP"; head -c 500 /tmp/tbm_start_4.json; echo
[[ "$HTTP" == "400" ]] || { echo "EXPECTED 400" >&2; exit 10; }
grep -q 'required' /tmp/tbm_start_4.json || { echo "EXPECTED required-fields message" >&2; exit 11; }
PASS=$((PASS+1))

echo "=== tbm-start: ${PASS}/4 PASS ==="
echo "SESSION_ID=$SESSION_ID"
