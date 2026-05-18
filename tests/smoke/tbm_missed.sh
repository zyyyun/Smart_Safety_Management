#!/bin/bash
# tests/smoke/tbm_missed.sh — Phase 9 TBM-03 curl smoke (Plan 09-02 Task 2)
# Usage: SESSION_ID=<N> bash tests/smoke/tbm_missed.sh
#   환경: SUPABASE_URL / SUPABASE_ANON_KEY (.env), SESSION_ID (orchestrator)
#   사이드이펙트: FCM push (실제 데이터 INSERT 없음 — push-only D-09)
#   주의: tbm-end 이전에 호출해야 함 (session ended 후에는 무관 — defensive)
#
# Tests (per 09-02-PLAN.md Task 2 step 5):
#   1. 정상: cron 이 발사하는 payload 흉내. testuser_w1 가 checkin 완료 (이전 smoke)
#      → missed = [testuser_w2, testuser_w3], recipients = [w2, w3, testuser1]
#      → HTTP 200 + missed_count + notified_count (Pitfall 9 dedup 검증)
#   2. payload 누락 → 400: session_id 생략
#   3. no-missed (leader-only): 모든 worker (w1·w2·w3) 가 checkin 완료 상태
#      → missed_count=0, notified_count = leader 1명
#
# Pitfall 9 검증: recipients 의 leader 중복 0 (sendPushToUsers 내부 Set dedup).

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
[[ -f "$SCRIPT_DIR/.env" ]] || { echo "ERROR: .env 없음 ($SCRIPT_DIR/.env)" >&2; exit 1; }
set -a; source "$SCRIPT_DIR/.env"; set +a
: "${SUPABASE_URL:?SUPABASE_URL not set}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY not set (.env)}"
: "${SUPABASE_SERVICE_ROLE_KEY:?SUPABASE_SERVICE_ROLE_KEY not set (.env)}"
: "${SESSION_ID:?SESSION_ID env required (use orchestrator or pre-export)}"
URL="${SUPABASE_URL%/}/functions/v1/notifications"

PASS=0

echo "=== Test 1: 정상 (missed = w2/w3, recipients = w2+w3+leader) ==="
HTTP=$(curl -sS -o /tmp/tbm_missed_1.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"tbm-missed\",\"session_id\":$SESSION_ID,\"group_id\":1,\"leader_user_id\":\"testuser1\"}")
echo "  HTTP $HTTP"; head -c 500 /tmp/tbm_missed_1.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200" >&2; exit 2; }
grep -q '"ok":true' /tmp/tbm_missed_1.json || { echo "EXPECTED ok:true" >&2; exit 3; }
grep -q '"missed_count":2' /tmp/tbm_missed_1.json || { echo "EXPECTED missed_count:2 (w2+w3)" >&2; exit 4; }
PASS=$((PASS+1))

echo "=== Test 2: payload 누락 → 400 (session_id 생략) ==="
HTTP=$(curl -sS -o /tmp/tbm_missed_2.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d '{"action":"tbm-missed","group_id":1,"leader_user_id":"testuser1"}')
echo "  HTTP $HTTP"; head -c 500 /tmp/tbm_missed_2.json; echo
[[ "$HTTP" == "400" ]] || { echo "EXPECTED 400" >&2; exit 5; }
grep -q 'required' /tmp/tbm_missed_2.json || { echo "EXPECTED required-fields message" >&2; exit 6; }
PASS=$((PASS+1))

echo "=== Test 3: prep — w2/w3 checkin → all-checked-in 상태 ==="
for U in testuser_w2 testuser_w3; do
  H=$(curl -sS -o /tmp/tbm_missed_prep_$U.json -w "%{http_code}" -X POST "$URL" \
      -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
      -H "Content-Type: application/json" \
      -d "{\"action\":\"tbm-checkin\",\"session_id\":$SESSION_ID,\"user_id\":\"$U\"}")
  echo "  prep $U: HTTP $H"
  [[ "$H" == "200" ]] || { echo "ERROR: prep checkin $U failed (HTTP $H)" >&2; exit 7; }
done

echo "=== Test 3: 정상 leader-only (모든 worker checked-in 상태) ==="
HTTP=$(curl -sS -o /tmp/tbm_missed_3.json -w "%{http_code}" -X POST "$URL" \
    -H "apikey: $SUPABASE_ANON_KEY" -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"action\":\"tbm-missed\",\"session_id\":$SESSION_ID,\"group_id\":1,\"leader_user_id\":\"testuser1\"}")
echo "  HTTP $HTTP"; head -c 500 /tmp/tbm_missed_3.json; echo
[[ "$HTTP" == "200" ]] || { echo "EXPECTED 200" >&2; exit 8; }
grep -q '"missed_count":0' /tmp/tbm_missed_3.json || { echo "EXPECTED missed_count:0 (all checked-in)" >&2; exit 9; }
PASS=$((PASS+1))

echo "=== tbm-missed: ${PASS}/3 PASS ==="
