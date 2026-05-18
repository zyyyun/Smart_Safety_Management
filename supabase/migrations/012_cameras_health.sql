-- ============================================
-- 012: Cameras Health (Phase 8 RTSP-03)
-- Phase 8 — per CONTEXT.md D-03 / D-09 + RESEARCH §Pattern 3 / Pitfalls 1·2·4·7
--
-- Adds:
--   (A) pg_net extension (RESEARCH 정정 #2 / Pitfall 1) — net.http_post 가용
--   (B) ALTER public.cameras : last_frame_at / health_state / last_alert_at
--   (C) Vault SQL 시드 (best-effort) : edge_function_base_url
--       — service_role_key 는 git/마이그 노출 회피 위해 dashboard 시드 필수 (T-8-01)
--       — SQL 시드 실패는 RAISE NOTICE 로 흡수 (CONTEXT.md A2 [ASSUMED])
--   (D) public.cameras_healthcheck() — SECURITY DEFINER plpgsql 함수
--       · 5분 임계 + 30분 cooldown + NULL 가드 (Pitfall 4) + ok↔down 전이 (Phase 4 D-09)
--       · Vault.decrypted_secrets 에서 sr_key + base_url SELECT (RESEARCH 정정 #3)
--       · net.http_post 으로 notifications Edge Function 'camera-down'/'camera-recovered' 호출
--   (E) pg_cron 'cameras_healthcheck_minute' 1분 주기 등록 (010 idempotent 패턴)
--
-- 의존: 001_extensions.sql 가 pg_cron 활성화, 002_tables.sql 가 public.cameras 생성,
--       003_rls_policies.sql 가 cameras_update_manager 정책 등록 (T-8-02 회귀 가드).
-- 적용: supabase db push --linked --yes
-- ============================================

-- ============================================
-- (A) pg_net 활성화 — Pitfall 1 / RESEARCH 정정 #2
--   Supabase managed Postgres 는 pg_net 을 pre-installed 하지만 default-enabled 는 아님.
--   pg_net 의 함수는 항상 net.* 네임스페이스에 hardcode 됨 — WITH SCHEMA extensions 는
--   메타데이터 위치만 지정 (함수 호출은 net.http_post 그대로).
-- ============================================
CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions;

-- ============================================
-- (B) ALTER public.cameras  (CONTEXT D-03)
--   - last_frame_at: snapshot.capture 성공 시점 (plan 08-03 의 wiring 으로 갱신)
--   - health_state: ENUM-like CHECK constraint, 'unknown' 초기값 (Pitfall 4 폭발 0)
--   - last_alert_at: 30분 cooldown + ok↔down 전이 시점
-- ============================================
ALTER TABLE public.cameras
    ADD COLUMN IF NOT EXISTS last_frame_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS health_state   TEXT DEFAULT 'unknown'
        CHECK (health_state IN ('ok','degraded','down','unknown')),
    ADD COLUMN IF NOT EXISTS last_alert_at  TIMESTAMPTZ;

-- ============================================
-- (C) Vault 시드 — best-effort idempotent SQL (CONTEXT A2 [ASSUMED])
--   service_role_key 는 dashboard 에서 수동 시드 (T-8-01 — git 노출 회피).
--   본 마이그는 edge_function_base_url 만 SQL 로 시드 시도.
--   Vault API (vault.create_secret) 호출 — 실패 시 RAISE NOTICE 로 흡수 (cameras_healthcheck
--   함수가 sr_key/base_url NULL 일 때 RAISE WARNING + RETURN 으로 graceful fallback).
-- ============================================
DO $$
DECLARE
    v_existing UUID;
BEGIN
    -- 이미 동일 name 의 secret 이 있으면 skip (idempotent — 마이그 재실행 안전)
    SELECT id INTO v_existing
        FROM vault.secrets
        WHERE name = 'edge_function_base_url'
        LIMIT 1;

    IF v_existing IS NULL THEN
        PERFORM vault.create_secret(
            'https://xbjqxnvemcqubjfflain.supabase.co/functions/v1',
            'edge_function_base_url',
            'Phase 8 — Edge Function base URL for cameras_healthcheck() (camera-down/recovered)'
        );
        RAISE NOTICE 'vault.secrets seeded: edge_function_base_url';
    ELSE
        RAISE NOTICE 'vault.secrets already has edge_function_base_url — skip';
    END IF;
EXCEPTION WHEN OTHERS THEN
    -- Vault API 권한/스키마 차이로 실패 가능 — silent skip + 대시보드 수동 시드 fallback.
    RAISE NOTICE 'vault seed skipped (dashboard 시드 fallback): %', SQLERRM;
END $$;

-- service_role_key 는 SQL 시드하지 않음 (T-8-01 — JWT git 노출 0건).
-- SUMMARY.md 의 User Setup Required 에 dashboard 시드 수동 절차 기재.

-- ============================================
-- (D) public.cameras_healthcheck()  (CONTEXT D-03 + Phase 4 D-09)
--   SECURITY DEFINER (Pitfall 7) — cron 실행 user 권한 무관, function owner 권한으로 동작.
--   호출 패턴:
--     (a) DOWN 전이: last_frame_at NOT NULL (Pitfall 4) AND < now()-5min AND health != 'down'
--                    AND last_alert_at NULL OR < now()-30min (30분 cooldown)
--                    → UPDATE health_state='down' + last_alert_at=now()
--                    → net.http_post('camera-down', ...)
--     (b) RECOVERY 전이: health='down' AND last_frame_at >= now()-5min
--                    → UPDATE health_state='ok' + last_alert_at=now()
--                    → net.http_post('camera-recovered', ...)
--   Phase 4 D-09 알림 전이 원칙 — ok↔down 전이 시점에만 1회, 같은 상태 지속 중 반복 X.
-- ============================================
CREATE OR REPLACE FUNCTION public.cameras_healthcheck() RETURNS void
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public, extensions, net
AS $$
DECLARE
    r        RECORD;
    sr_key   TEXT;
    base_url TEXT;
BEGIN
    -- Vault 에서 secrets SELECT (RESEARCH 정정 #3 — current_setting GUC 패턴 거부)
    SELECT decrypted_secret INTO sr_key
        FROM vault.decrypted_secrets WHERE name = 'service_role_key' LIMIT 1;
    SELECT decrypted_secret INTO base_url
        FROM vault.decrypted_secrets WHERE name = 'edge_function_base_url' LIMIT 1;

    IF sr_key IS NULL OR base_url IS NULL THEN
        -- Vault 시드 미완료 시 silent skip (대시보드 시드 후 다음 cron tick 부터 동작).
        RAISE WARNING 'cameras_healthcheck: vault secret missing (sr_key_present=%, base_url_present=%) — skip',
            (sr_key IS NOT NULL), (base_url IS NOT NULL);
        RETURN;
    END IF;

    -- ─── (a) DOWN 전이 ───
    -- Pitfall 4: last_frame_at IS NOT NULL 가드 — 신규 cameras (last_frame_at=NULL)
    --   가 즉시 'down' 폭발하는 것 차단. snapshot.capture 첫 성공 후부터 활성.
    FOR r IN
        SELECT camera_id, group_id, last_frame_at
        FROM public.cameras
        WHERE last_frame_at IS NOT NULL
          AND last_frame_at < now() - INTERVAL '5 minutes'
          AND health_state IS DISTINCT FROM 'down'
          AND (last_alert_at IS NULL OR last_alert_at < now() - INTERVAL '30 minutes')
    LOOP
        -- 상태 전이 먼저 (race 방지 — http_post 실패해도 다음 cycle 에 재시도 안 됨,
        --   30분 cooldown 후 재발사). D-09 의 "전이 시점에만 1회" 보존.
        UPDATE public.cameras
            SET health_state = 'down', last_alert_at = now()
            WHERE camera_id = r.camera_id;

        -- pg_net 비동기 fire-and-forget. response 무시.
        PERFORM net.http_post(
            url     := base_url || '/notifications',
            headers := jsonb_build_object(
                'Authorization', 'Bearer ' || sr_key,
                'Content-Type',  'application/json'
            ),
            body    := jsonb_build_object(
                'action',        'camera-down',
                'camera_id',     r.camera_id,
                'group_id',      r.group_id,
                'last_frame_at', r.last_frame_at
            )
        );
    END LOOP;

    -- ─── (b) RECOVERY 전이 (D-09 종료 알림 패턴) ───
    FOR r IN
        SELECT camera_id, group_id
        FROM public.cameras
        WHERE health_state = 'down'
          AND last_frame_at IS NOT NULL
          AND last_frame_at >= now() - INTERVAL '5 minutes'
    LOOP
        UPDATE public.cameras
            SET health_state = 'ok', last_alert_at = now()
            WHERE camera_id = r.camera_id;

        PERFORM net.http_post(
            url     := base_url || '/notifications',
            headers := jsonb_build_object(
                'Authorization', 'Bearer ' || sr_key,
                'Content-Type',  'application/json'
            ),
            body    := jsonb_build_object(
                'action',    'camera-recovered',
                'camera_id', r.camera_id,
                'group_id',  r.group_id
            )
        );
    END LOOP;
END;
$$;

COMMENT ON FUNCTION public.cameras_healthcheck() IS
    'Phase 8 RTSP-03: 5분 무수신 카메라 ok→down 전이 + 30분 cooldown + FCM 알림. '
    '회복 시 down→ok 전이 + 종료 알림. Phase 4 D-09 알림 전이 원칙 적용.';

-- ============================================
-- (E) pg_cron 1분 주기 등록 (010 idempotent 패턴)
--   동일 이름 job 이 이미 있으면 unschedule 먼저 (재실행 안전).
-- ============================================
DO $$
BEGIN
    PERFORM cron.unschedule('cameras_healthcheck_minute');
EXCEPTION WHEN OTHERS THEN
    -- 처음 실행 시 unschedule 가 not-found 로 실패하는 것이 정상 → 무시.
    NULL;
END $$;

SELECT cron.schedule(
    'cameras_healthcheck_minute',
    '* * * * *',
    $$SELECT public.cameras_healthcheck();$$
);

-- ============================================
-- Migration 012 complete.
-- ============================================
