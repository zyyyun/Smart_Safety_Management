#!/bin/bash
# tests/smoke/tbm_all.sh — Phase 9 TBM Plan 09-02 12 smoke orchestrator
# Usage: bash tests/smoke/tbm_all.sh
#
# 순서:
#   0) prep — service_role 로 today/group_id=1 잔존 session 정리 (전회차 잔존 회피)
#   0) prep — notifications row count BEFORE (service_role, D-09 회귀 가드 baseline)
#   1) tbm_start.sh (3 scenarios — 200/409/400) → SESSION_ID extract
#   2) tbm_checkin.sh (3 scenarios — 200/403/200 idempotent)
#   3) tbm_missed.sh (3 scenarios — 200 missed/400/200 leader-only) — w2·w3 checkin prep 내장
#   4) tbm_end.sh (3 scenarios — 404/400/200) → session 종료 (cleanup 자연)
#   5) D-09 회귀 가드 — notifications row count AFTER + delta=0 검증
#
# 12 smoke ALL PASS + delta=0 인 경우 'TBM-02 ALL GREEN' echo 후 exit 0.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
[[ -f "$SCRIPT_DIR/.env" ]] || { echo "ERROR: .env 없음" >&2; exit 1; }
set -a; source "$SCRIPT_DIR/.env"; set +a
: "${SUPABASE_URL:?SUPABASE_URL not set}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY not set}"
: "${SUPABASE_SERVICE_ROLE_KEY:?SUPABASE_SERVICE_ROLE_KEY not set}"

echo "════════════════════════════════════════════════════════════════════"
echo " Phase 9 Plan 09-02 — TBM 12 smoke orchestrator"
echo "════════════════════════════════════════════════════════════════════"

# ── Step 0a: cleanup 잔존 today/group_id=1 session (전회차 회피 — Phase 8 03 패턴) ──
TODAY=$(date -u +%Y-%m-%d)
echo "[0a] cleanup: DELETE today session group_id=1 (service_role)"
DEL=$(curl -sS -o /tmp/tbm_cleanup.json -w "%{http_code}" -X DELETE \
    "$SUPABASE_URL/rest/v1/tbm_sessions?group_id=eq.1&session_date=eq.$TODAY" \
    -H "apikey: $SUPABASE_SERVICE_ROLE_KEY" \
    -H "Authorization: Bearer $SUPABASE_SERVICE_ROLE_KEY" \
    -H "Prefer: return=minimal")
echo "  cleanup HTTP $DEL"
[[ "$DEL" == "204" || "$DEL" == "200" ]] || { echo "ERROR: cleanup failed ($DEL)" >&2; exit 1; }

# ── Step 0b: notifications BEFORE count (D-09 회귀 가드, advisor: service_role 사용) ──
echo "[0b] D-09 baseline: notifications row count BEFORE (service_role)"
BEFORE=$(curl -sS -I -X GET \
    "$SUPABASE_URL/rest/v1/notifications?select=notification_id" \
    -H "apikey: $SUPABASE_SERVICE_ROLE_KEY" \
    -H "Authorization: Bearer $SUPABASE_SERVICE_ROLE_KEY" \
    -H "Prefer: count=exact" -H "Range: 0-0" \
    | grep -i 'content-range' | sed -E 's/.*\/([0-9]+).*/\1/' | tr -d '\r')
echo "  notifications BEFORE=$BEFORE"
[[ -n "$BEFORE" ]] || { echo "ERROR: BEFORE count extract failed" >&2; exit 1; }

# ── Step 1: tbm_start (3 scenarios) ──
echo ""
echo "▶ [1] tbm_start.sh"
START_OUT=$(bash "$SCRIPT_DIR/tests/smoke/tbm_start.sh" 2>&1)
echo "$START_OUT"
SESSION_ID=$(echo "$START_OUT" | grep -E '^SESSION_ID=' | tail -1 | cut -d= -f2)
[[ -n "$SESSION_ID" ]] || { echo "ERROR: SESSION_ID extract failed" >&2; exit 2; }
echo "  ▶ extracted SESSION_ID=$SESSION_ID"
export SESSION_ID

# ── Step 2: tbm_checkin (3 scenarios) ──
echo ""
echo "▶ [2] tbm_checkin.sh (SESSION_ID=$SESSION_ID)"
bash "$SCRIPT_DIR/tests/smoke/tbm_checkin.sh"

# ── Step 3: tbm_missed (3 scenarios — w2·w3 checkin prep 포함) ──
echo ""
echo "▶ [3] tbm_missed.sh (SESSION_ID=$SESSION_ID)"
bash "$SCRIPT_DIR/tests/smoke/tbm_missed.sh"

# ── Step 4: tbm_end (3 scenarios) ──
echo ""
echo "▶ [4] tbm_end.sh (SESSION_ID=$SESSION_ID)"
bash "$SCRIPT_DIR/tests/smoke/tbm_end.sh"

# ── Step 5: D-09 회귀 가드 — notifications AFTER count + delta=0 ──
echo ""
echo "[5] D-09 가드: notifications row count AFTER (service_role)"
AFTER=$(curl -sS -I -X GET \
    "$SUPABASE_URL/rest/v1/notifications?select=notification_id" \
    -H "apikey: $SUPABASE_SERVICE_ROLE_KEY" \
    -H "Authorization: Bearer $SUPABASE_SERVICE_ROLE_KEY" \
    -H "Prefer: count=exact" -H "Range: 0-0" \
    | grep -i 'content-range' | sed -E 's/.*\/([0-9]+).*/\1/' | tr -d '\r')
DELTA=$((AFTER - BEFORE))
echo "  notifications BEFORE=$BEFORE  AFTER=$AFTER  delta=$DELTA"
[[ "$DELTA" == "0" ]] || { echo "FAIL: D-09 push-only 회귀 — delta=$DELTA (expected 0)" >&2; exit 3; }

echo ""
echo "════════════════════════════════════════════════════════════════════"
echo " TBM-02 ALL GREEN: 12 smoke PASS + D-09 delta=0"
echo "════════════════════════════════════════════════════════════════════"
