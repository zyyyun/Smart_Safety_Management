#!/usr/bin/env python3
"""
Phase 7 / D-05 fallback — 단축 PoC 미실행 또는 실패 시 시연 데이터 시드.

실측 PoC (Phase 4 04-04) 가 우선 — 본 스크립트는 시연 직전 fallback.

사용:
  python scripts/seed_watch_demo.py --device-id 1 --duration-min 120
  python scripts/seed_watch_demo.py --device-id 1 --duration-min 120 --wipe

환경 변수 (.env 에서 읽음 — Phase 4 03 패턴):
  SUPABASE_URL                  https://<project>.supabase.co
  SUPABASE_SERVICE_ROLE_KEY     service_role key (RLS 우회용 — 본 스크립트 한정)

산출물:
  - minute_summary: --duration-min 행 (default 120, good_ratio 0.7~0.95 분포)
  - wear_state_events: 3 행 (WARMUP→WORN, WORN→OFF, OFF→WORN — REMOVED 시나리오)
  - safety_alerts: 2 행 (REMOVED 발생 + REMOVED 해소 — resolved_at 채움 = D-09 전이)

안전:
  - --wipe 플래그 없으면 INSERT 만 (기존 데이터 보존)
  - --wipe 시 device_id 의 (minute_summary, wear_state_events, safety_alerts) DELETE 후 시드
  - raw_events 는 시드 안 함 (5–6Hz × 7200s = 40k 행 → fallback 가치 낮음)
"""

from __future__ import annotations
import argparse
import json
import os
import random
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
ENV_FILE = REPO_ROOT / ".env"


def load_env() -> dict:
    """Phase 4 03 패턴 — .env 파일 직접 파싱 (python-dotenv 무 의존)."""
    env: dict = {}
    if not ENV_FILE.exists():
        print(
            f"ERROR: {ENV_FILE} 없음. .env.example 복사 + service_role key 채우세요.",
            file=sys.stderr,
        )
        sys.exit(1)
    for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip().strip('"').strip("'")
    for key in ("SUPABASE_URL", "SUPABASE_SERVICE_ROLE_KEY"):
        if key not in env:
            print(f"ERROR: .env 에 {key} 없음", file=sys.stderr)
            sys.exit(1)
    return env


def supabase_request(env: dict, method: str, path: str, body=None) -> tuple:
    url = f"{env['SUPABASE_URL'].rstrip('/')}/rest/v1/{path}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "apikey": env["SUPABASE_SERVICE_ROLE_KEY"],
            "Authorization": f"Bearer {env['SUPABASE_SERVICE_ROLE_KEY']}",
            "Content-Type": "application/json",
            "Prefer": "return=representation",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")


def wipe_device(env: dict, device_id: int) -> None:
    for table in ("safety_alerts", "wear_state_events", "minute_summary"):
        status, body = supabase_request(env, "DELETE", f"{table}?device_id=eq.{device_id}")
        print(f"[WIPE] {table} device_id={device_id} -> HTTP {status}")


def seed_minute_summary(env: dict, device_id: int, duration_min: int, start: datetime) -> int:
    rows = []
    for i in range(duration_min):
        minute_ts = start + timedelta(minutes=i)
        # 시연 시나리오: 0~30 분 = WORN 정상, 30~35 분 = OFF (REMOVED), 35~120 분 = WORN 복귀
        if 30 <= i < 35:
            state = "OFF"
            hr = None
            temp = round(random.uniform(28.0, 30.0), 1)
            good = round(random.uniform(0.20, 0.35), 2)  # 결측 임계 (D-17 = 0.30) 근처
        else:
            state = "WORN"
            hr = random.randint(72, 92)
            temp = round(random.uniform(35.6, 36.4), 1)
            good = round(random.uniform(0.70, 0.95), 2)
        rows.append({
            "device_id": device_id,
            "minute_ts": minute_ts.isoformat(),
            "hr_median": hr,
            "temp_median": temp,
            "temp_iqr": round(random.uniform(0.1, 0.5), 1),
            "steps_delta": random.randint(0, 30) if state == "WORN" else 0,
            "dominant_state": state,
            "good_ratio": good,
        })
    status, body = supabase_request(env, "POST", "minute_summary", rows)
    if status >= 300:
        print(f"ERROR minute_summary insert: {status} {body}", file=sys.stderr)
        sys.exit(2)
    return len(rows)


def seed_wear_state_events(env: dict, device_id: int, start: datetime) -> int:
    rows = [
        # (a) WARMUP -> WORN at +1 min
        {"device_id": device_id, "ts": (start + timedelta(minutes=1)).isoformat(),
         "from_state": "WARMUP", "to_state": "WORN", "reason": {"trigger": "temp>=T_warm"}},
        # (b) WORN -> OFF at +30 min (REMOVED 시작)
        {"device_id": device_id, "ts": (start + timedelta(minutes=30)).isoformat(),
         "from_state": "WORN", "to_state": "OFF", "reason": {"trigger": "temp<T_off"}},
        # (c) OFF -> WORN at +35 min (REMOVED 해소)
        {"device_id": device_id, "ts": (start + timedelta(minutes=35)).isoformat(),
         "from_state": "OFF", "to_state": "WORN", "reason": {"trigger": "temp>=T_warm"}},
    ]
    status, body = supabase_request(env, "POST", "wear_state_events", rows)
    if status >= 300:
        print(f"ERROR wear_state_events insert: {status} {body}", file=sys.stderr)
        sys.exit(2)
    return len(rows)


def seed_safety_alerts(env: dict, device_id: int, start: datetime) -> int:
    # D-09 알림 전이 원칙: REMOVED 발생 1 건 (resolved_at NULL) + REMOVED 해소 1 건 (resolved_at 채움)
    # 시연 시 ack 버튼으로 ack_at 채움 (Wave 3)
    raised = (start + timedelta(minutes=30)).isoformat()
    resolved = (start + timedelta(minutes=35)).isoformat()
    rows = [
        {"device_id": device_id, "alert_type": "REMOVED", "severity": "WARNING",
         "raised_at": raised, "resolved_at": None, "ack_at": None,
         "reason": {"source": "seed_watch_demo.py", "trigger": "OFF>=5min"}},
        {"device_id": device_id, "alert_type": "REMOVED", "severity": "WARNING",
         "raised_at": raised, "resolved_at": resolved, "ack_at": None,
         "reason": {"source": "seed_watch_demo.py", "trigger": "REMOVED resolved"}},
    ]
    status, body = supabase_request(env, "POST", "safety_alerts", rows)
    if status >= 300:
        print(f"ERROR safety_alerts insert: {status} {body}", file=sys.stderr)
        sys.exit(2)
    return len(rows)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--device-id", type=int, required=True, help="testuser1 J2208A device_id (010 시드)")
    ap.add_argument("--duration-min", type=int, default=120, help="minute_summary 행 수 (default 120)")
    ap.add_argument("--wipe", action="store_true", help="시드 전 device 의 기존 행 삭제")
    args = ap.parse_args()

    env = load_env()
    if args.wipe:
        wipe_device(env, args.device_id)

    random.seed(42)  # 재현 가능
    start = datetime.now(timezone.utc) - timedelta(minutes=args.duration_min)

    n_summary = seed_minute_summary(env, args.device_id, args.duration_min, start)
    n_wear = seed_wear_state_events(env, args.device_id, start)
    n_alert = seed_safety_alerts(env, args.device_id, start)

    print(f"\n[SEED COMPLETE] device_id={args.device_id}")
    print(f"  minute_summary: {n_summary} rows")
    print(f"  wear_state_events: {n_wear} rows")
    print(f"  safety_alerts: {n_alert} rows")


if __name__ == "__main__":
    main()
