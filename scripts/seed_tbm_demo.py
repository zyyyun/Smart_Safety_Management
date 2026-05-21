#!/usr/bin/env python3
"""
Phase 9 Plan 09-01 prerequisite — group_id=1 에 worker 3명 시드.

testuser1 (manager, group_id=1) 가 이미 존재. TBM PoC 시연 시 다른 worker 가
있어야 tbm-start FCM 다중 수신 + tbm-missed 알림 다중 발사 검증 가능. 본 스크립트
는 testuser_w1·w2·w3 (worker, group_id=1) 3명을 idempotent 시드.

사용:
  python scripts/seed_tbm_demo.py
  python scripts/seed_tbm_demo.py --wipe   # 기존 testuser_w* 제거 후 시드 (시연 reset)

환경 변수 (.env 에서 읽음 — Phase 4 03 / Phase 7 01 패턴):
  SUPABASE_URL                  https://<project>.supabase.co
  SUPABASE_SERVICE_ROLE_KEY     service_role key (RLS 우회용 — 본 스크립트 한정)

산출물:
  - profiles row 3개 (testuser_w1·w2·w3, user_role='worker', group_id=1, name='작업자 1·2·3')
  - 기존 row 보존 (ON CONFLICT (user_id) DO NOTHING — Prefer: resolution=ignore-duplicates)

안전:
  - profiles.id 는 auth.users(id) FK 라서 auth 시스템 사용 시 미가입 worker 는
    실제 로그인 X. 본 v1.0 PoC 한정 — DB row 만 존재해도 FCM 송신/tbm_participants
    insert 는 동작 (user_id 만 키로 사용).
  - PoC 한정: 본 worker 들은 실제 FCM 등록 X (fcm_token NULL) — 알림 발사 실패는
    expected. Plan 09-02 의 sendPushToUsers 가 fcm_token NULL 인 사용자 silent skip.
"""

from __future__ import annotations
import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
ENV_FILE = REPO_ROOT / ".env"

# group_id=1 에 시드할 worker 3명 (D-04 미참여 대상 계산 + tbm-start FCM 다중 수신용)
WORKERS = [
    {"user_id": "testuser_w1", "name": "작업자 1", "user_role": "worker", "group_id": 1},
    {"user_id": "testuser_w2", "name": "작업자 2", "user_role": "worker", "group_id": 1},
    {"user_id": "testuser_w3", "name": "작업자 3", "user_role": "worker", "group_id": 1},
]


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


def supabase_request(env: dict, method: str, path: str, body=None, prefer: str = "return=representation") -> tuple:
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
            "Prefer": prefer,
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")


def wipe_workers(env: dict) -> None:
    """profiles + auth.users 모두 wipe — ON DELETE CASCADE 동작."""
    for w in WORKERS:
        uid = w["user_id"]
        email = f"{uid}@tbm-poc.local"
        # auth.users 삭제 → profiles ON DELETE CASCADE 동작
        auth_id = lookup_auth_user_by_email(env, email)
        if auth_id:
            url = f"{env['SUPABASE_URL'].rstrip('/')}/auth/v1/admin/users/{auth_id}"
            req = urllib.request.Request(
                url, method="DELETE",
                headers={
                    "apikey": env["SUPABASE_SERVICE_ROLE_KEY"],
                    "Authorization": f"Bearer {env['SUPABASE_SERVICE_ROLE_KEY']}",
                },
            )
            try:
                with urllib.request.urlopen(req, timeout=15) as resp:
                    print(f"[WIPE] auth.users {uid} ({auth_id}) -> HTTP {resp.status}")
            except urllib.error.HTTPError as e:
                print(f"[WIPE] auth.users {uid} -> HTTP {e.code} {e.read().decode('utf-8', errors='replace')[:100]}")
        else:
            # profiles 만 정리 (auth.users 없을 때)
            status, _ = supabase_request(env, "DELETE", f"profiles?user_id=eq.{uid}", prefer="return=minimal")
            print(f"[WIPE] profiles user_id={uid} -> HTTP {status}")


def get_existing_workers(env: dict) -> set:
    """현재 group_id=1 에 존재하는 worker user_id set."""
    ids = ",".join(f'"{w["user_id"]}"' for w in WORKERS)
    status, body = supabase_request(
        env, "GET", f"profiles?select=user_id&user_id=in.({ids})", body=None
    )
    if status >= 300:
        print(f"WARNING: existing worker lookup failed: {status} {body}", file=sys.stderr)
        return set()
    try:
        rows = json.loads(body)
        return {row["user_id"] for row in rows}
    except json.JSONDecodeError:
        return set()


def create_auth_user(env: dict, user_id: str) -> str | None:
    """Supabase Auth Admin API 로 auth.users row 생성.

    profiles.id 는 auth.users(id) FK 라서 본 row 먼저 필요. 002_tables.sql 의
    handle_new_user() 트리거가 auth.users INSERT 시점에 자동으로 profiles row
    (id, user_id, email, created_at) 를 생성한다. 본 함수는 user_metadata 에
    user_id 를 전달해서 트리거가 옳은 user_id ('testuser_w1') 를 박도록 한다.

    Returns: 생성된 auth user id (UUID), 실패 시 None.
    """
    # email 은 Supabase Auth 가 unique 요구. testuser_w1@tbm-poc.local 형식.
    email = f"{user_id}@tbm-poc.local"
    url = f"{env['SUPABASE_URL'].rstrip('/')}/auth/v1/admin/users"
    # NOTE: handle_new_user() 트리거가 raw_user_meta_data->>'user_id' 를 사용
    # → Supabase Admin API 의 user_metadata 가 raw_user_meta_data 와 동일.
    payload = {
        "email": email,
        "password": "tbm-poc-no-login",   # PoC 한정 — 실제 로그인 X
        "email_confirm": True,             # 확인 메일 skip
        "user_metadata": {
            "user_id": user_id,            # 트리거가 profiles.user_id 에 박음
            "poc_seed": "phase-9-tbm",
        },
    }
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        method="POST",
        headers={
            "apikey": env["SUPABASE_SERVICE_ROLE_KEY"],
            "Authorization": f"Bearer {env['SUPABASE_SERVICE_ROLE_KEY']}",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            return body.get("id")
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace")
        # 이미 존재하는 경우 — email lookup 으로 id 회수
        if "already" in err_body.lower() or e.code == 422:
            return lookup_auth_user_by_email(env, email)
        print(f"WARNING auth.users create failed for {user_id}: HTTP {e.code} {err_body[:200]}", file=sys.stderr)
        return None


def lookup_auth_user_by_email(env: dict, email: str) -> str | None:
    """기존 auth.users id 회수 (이미 존재 시)."""
    url = f"{env['SUPABASE_URL'].rstrip('/')}/auth/v1/admin/users"
    req = urllib.request.Request(
        url,
        method="GET",
        headers={
            "apikey": env["SUPABASE_SERVICE_ROLE_KEY"],
            "Authorization": f"Bearer {env['SUPABASE_SERVICE_ROLE_KEY']}",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            users = body.get("users", [])
            for u in users:
                if u.get("email") == email:
                    return u.get("id")
    except urllib.error.HTTPError as e:
        print(f"WARNING auth.users lookup failed: {e.code} {e.read().decode('utf-8', errors='replace')[:200]}", file=sys.stderr)
    return None


def seed_workers(env: dict) -> tuple[int, int]:
    """profiles 에 worker 3명 시드. auth.users 생성 → 트리거 (handle_new_user) 가
    profiles row 자동 생성 (id, user_id, email) → 본 함수가 PATCH 로 name/user_role/
    group_id 채움.

    Returns: (inserted_count, skipped_count)
    """
    existing = get_existing_workers(env)
    to_insert = [w for w in WORKERS if w["user_id"] not in existing]
    skipped = len(WORKERS) - len(to_insert)
    if not to_insert:
        return 0, skipped

    updated = 0
    for w in to_insert:
        # auth.users row 생성 (트리거가 자동 profiles row insert — user_metadata.user_id 가 user_id 박힘)
        auth_id = create_auth_user(env, w["user_id"])
        if auth_id is None:
            print(f"WARNING: auth.users 생성 실패 — {w['user_id']} skip", file=sys.stderr)
            continue

        # UPSERT by id — 트리거가 자동 생성한 row 가 있으면 갱신, 없으면 생성.
        # user_id 도 PATCH 함 (트리거가 user_metadata 부재로 UUID 박은 경우 정정).
        upsert = {
            "id": auth_id,
            "user_id": w["user_id"],
            "name": w["name"],
            "user_role": w["user_role"],
            "group_id": w["group_id"],
        }
        status, body = supabase_request(
            env, "POST",
            "profiles?on_conflict=id",
            body=[upsert],
            prefer="resolution=merge-duplicates,return=minimal",
        )
        if status >= 300:
            print(f"WARNING profiles UPSERT {w['user_id']} ({auth_id}): {status} {body}", file=sys.stderr)
            continue
        updated += 1
        print(f"[SEED] {w['user_id']:15s} ({auth_id}) -> profile UPSERT HTTP {status}")

    if updated == 0:
        print("ERROR: 0 worker 시드됨", file=sys.stderr)
        sys.exit(2)
    return updated, skipped


def verify_workers(env: dict) -> int:
    """group_id=1 의 worker 수 GET 검증."""
    status, body = supabase_request(
        env, "GET",
        "profiles?select=user_id,name,user_role,group_id&group_id=eq.1&user_role=eq.worker",
        body=None,
    )
    if status >= 300:
        print(f"WARNING verify failed: {status} {body}", file=sys.stderr)
        return -1
    try:
        rows = json.loads(body)
        print(f"\n[VERIFY] group_id=1 worker count = {len(rows)}")
        for r in rows:
            print(f"  - {r['user_id']:15s} {r['name']:10s} role={r['user_role']}")
        return len(rows)
    except json.JSONDecodeError:
        return -1


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--wipe", action="store_true", help="시드 전 기존 testuser_w* 제거")
    args = ap.parse_args()

    env = load_env()
    if args.wipe:
        wipe_workers(env)

    inserted, skipped = seed_workers(env)
    print(f"seed_tbm_demo: inserted={inserted}, skipped={skipped}, workers={len(WORKERS)}")

    n = verify_workers(env)
    if n < 3:
        print(f"\n[FAIL] worker count={n} < 3 — Phase 9 PoC prerequisite 미충족", file=sys.stderr)
        sys.exit(3)
    print(f"\n[OK] Phase 9 PoC prerequisite 충족 (worker count={n} >= 3)")


if __name__ == "__main__":
    main()
