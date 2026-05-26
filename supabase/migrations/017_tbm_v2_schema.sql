-- Phase 12 - TBM v2 schema
--
-- Destructive migration: replaces Phase 9 TBM tables with the KOSHA-guide
-- aligned v2 shape. Run only after exporting existing tbm_* rows if they
-- matter in the target environment.

DROP TABLE IF EXISTS public.tbm_participants CASCADE;
DROP TABLE IF EXISTS public.tbm_checklists CASCADE;
DROP TABLE IF EXISTS public.tbm_sessions CASCADE;
DROP TABLE IF EXISTS public.tbm_templates CASCADE;

CREATE TABLE public.tbm_sessions (
    session_id        BIGSERIAL PRIMARY KEY,
    group_id          INTEGER NOT NULL REFERENCES public.groups(group_id),
    session_date      DATE NOT NULL DEFAULT CURRENT_DATE,
    work_scope        VARCHAR(80) NOT NULL,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at          TIMESTAMPTZ,
    expected_end_at   TIMESTAMPTZ NOT NULL,
    leader_user_id    VARCHAR(50) NOT NULL,
    work_type         VARCHAR(40) NOT NULL,
    location          VARCHAR(255),
    notes             TEXT,
    missed_alert_at   TIMESTAMPTZ,
    hazards_snapshot  JSONB NOT NULL DEFAULT '[]'::jsonb,
    controls_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
    key_hazard_id     VARCHAR(40),
    feedback_notes    TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT tbm_sessions_group_date_scope_uq UNIQUE (group_id, session_date, work_scope)
);

CREATE TABLE public.tbm_templates (
    template_id     SERIAL PRIMARY KEY,
    work_type       VARCHAR(40) NOT NULL UNIQUE,
    title           TEXT NOT NULL,
    description     TEXT,
    hazards         JSONB NOT NULL DEFAULT '[]'::jsonb,
    controls        JSONB NOT NULL DEFAULT '[]'::jsonb,
    key_actions     JSONB NOT NULL DEFAULT '[]'::jsonb,
    checks          JSONB NOT NULL DEFAULT '[]'::jsonb,
    target_detector VARCHAR(20),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    is_custom       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.tbm_checklists (
    checklist_id BIGSERIAL PRIMARY KEY,
    session_id   BIGINT NOT NULL REFERENCES public.tbm_sessions(session_id) ON DELETE CASCADE,
    item_idx     INTEGER NOT NULL,
    item_text    TEXT NOT NULL,
    is_checked   BOOLEAN NOT NULL DEFAULT FALSE,
    note         TEXT,
    checked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT tbm_checklists_session_item_uq UNIQUE (session_id, item_idx)
);

CREATE TABLE public.tbm_participants (
    participant_id BIGSERIAL PRIMARY KEY,
    session_id     BIGINT NOT NULL REFERENCES public.tbm_sessions(session_id) ON DELETE CASCADE,
    user_id        TEXT NOT NULL,
    signed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    signature_url  TEXT,
    method         TEXT NOT NULL DEFAULT 'signature',
    CONSTRAINT tbm_participants_session_user_uq UNIQUE (session_id, user_id),
    CONSTRAINT tbm_participants_method_chk CHECK (method IN ('signature', 'nfc', 'qr', 'manual'))
);

CREATE INDEX idx_tbm_sessions_group_date ON public.tbm_sessions(group_id, session_date);
CREATE INDEX idx_tbm_sessions_expected_open ON public.tbm_sessions(expected_end_at)
    WHERE ended_at IS NULL AND missed_alert_at IS NULL;
CREATE INDEX idx_tbm_checklists_session_idx ON public.tbm_checklists(session_id, item_idx);
CREATE INDEX idx_tbm_participants_session ON public.tbm_participants(session_id);
CREATE INDEX idx_tbm_templates_active ON public.tbm_templates(is_active) WHERE is_active = TRUE;

ALTER TABLE public.tbm_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tbm_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tbm_checklists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tbm_participants ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    CREATE POLICY "tbm_sessions_select_v2_poc" ON public.tbm_sessions
        FOR SELECT TO anon, authenticated USING (true);
    CREATE POLICY "tbm_templates_select_v2_poc" ON public.tbm_templates
        FOR SELECT TO anon, authenticated USING (true);
    CREATE POLICY "tbm_checklists_select_v2_poc" ON public.tbm_checklists
        FOR SELECT TO anon, authenticated USING (true);
    CREATE POLICY "tbm_participants_select_v2_poc" ON public.tbm_participants
        FOR SELECT TO anon, authenticated USING (true);

    -- Direct client checklist updates are retained from the Phase 9 TBM UI.
    CREATE POLICY "tbm_checklists_update_v2_poc" ON public.tbm_checklists
        FOR UPDATE TO anon, authenticated USING (true) WITH CHECK (true);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE
        public.tbm_sessions,
        public.tbm_templates,
        public.tbm_checklists,
        public.tbm_participants;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

INSERT INTO public.tbm_templates
    (work_type, title, description, hazards, controls, key_actions, checks, target_detector, is_active, is_custom)
VALUES
(
    'forklift',
    'Forklift movement',
    'Plating shop material movement with forklift or pallet jack.',
    '[
      {"id":"h1","text":"Collision with nearby worker"},
      {"id":"h2","text":"Blind spot while reversing"},
      {"id":"h3","text":"Load drop or tilt"},
      {"id":"h4","text":"Slippery floor near wet process"}
    ]'::jsonb,
    '[
      {"id":"c1","hazard_id":"h1","level":"control","text":"Separate pedestrian path before movement"},
      {"id":"c2","hazard_id":"h2","level":"control","text":"Use spotter for reversing or narrow aisle"},
      {"id":"c3","hazard_id":"h3","level":"substitute","text":"Keep load low and balanced"},
      {"id":"c4","hazard_id":"h4","level":"control","text":"Remove water and check floor condition before entry"}
    ]'::jsonb,
    '[
      {"id":"a1","text":"Confirm travel route"},
      {"id":"a2","text":"Check horn and warning light"},
      {"id":"a3","text":"Stop work if pedestrian path is mixed"}
    ]'::jsonb,
    '["Route is clear","Workers know the movement plan","Forklift warning devices checked","Load is secured"]'::jsonb,
    'forklift',
    TRUE,
    FALSE
),
(
    'chemical',
    'Chemical handling',
    'Acid, alkali, and plating solution handling.',
    '[
      {"id":"h1","text":"Splash to eye or skin"},
      {"id":"h2","text":"Fume exposure"},
      {"id":"h3","text":"Wrong chemical mixing"},
      {"id":"h4","text":"Spill around tank or transfer line"}
    ]'::jsonb,
    '[
      {"id":"c1","hazard_id":"h1","level":"control","text":"Wear face shield, goggles, gloves, and apron"},
      {"id":"c2","hazard_id":"h2","level":"control","text":"Confirm local exhaust and ventilation"},
      {"id":"c3","hazard_id":"h3","level":"eliminate","text":"Read label and SDS before transfer"},
      {"id":"c4","hazard_id":"h4","level":"control","text":"Prepare spill kit and rinse route"}
    ]'::jsonb,
    '[
      {"id":"a1","text":"Check PPE before opening container"},
      {"id":"a2","text":"Confirm SDS and label"},
      {"id":"a3","text":"Keep emergency wash path clear"}
    ]'::jsonb,
    '["PPE checked","Ventilation checked","SDS checked","Spill kit ready"]'::jsonb,
    'fire',
    TRUE,
    FALSE
),
(
    'hot_work',
    'Hot work and heat treatment',
    'Hot surfaces, drying, heating, and thermal process support.',
    '[
      {"id":"h1","text":"Burn from hot surface"},
      {"id":"h2","text":"Fire from nearby combustible material"},
      {"id":"h3","text":"Heat stress"},
      {"id":"h4","text":"Unexpected equipment restart"}
    ]'::jsonb,
    '[
      {"id":"c1","hazard_id":"h1","level":"control","text":"Use heat-resistant gloves and mark hot zone"},
      {"id":"c2","hazard_id":"h2","level":"eliminate","text":"Remove combustible material before start"},
      {"id":"c3","hazard_id":"h3","level":"control","text":"Set rest and hydration interval"},
      {"id":"c4","hazard_id":"h4","level":"eliminate","text":"Apply lockout before maintenance"}
    ]'::jsonb,
    '[
      {"id":"a1","text":"Clear combustible material"},
      {"id":"a2","text":"Confirm fire extinguisher location"},
      {"id":"a3","text":"Mark hot surface and isolation status"}
    ]'::jsonb,
    '["Combustibles cleared","Fire extinguisher nearby","Hot zone marked","Rest plan confirmed"]'::jsonb,
    'fire',
    TRUE,
    FALSE
);

CREATE OR REPLACE FUNCTION public.tbm_missed_attendance_check()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions, net
AS $$
DECLARE
    s RECORD;
    edge_url TEXT;
    sr_key TEXT;
BEGIN
    SELECT decrypted_secret INTO edge_url
    FROM vault.decrypted_secrets
    WHERE name = 'edge_function_base_url'
    LIMIT 1;

    SELECT decrypted_secret INTO sr_key
    FROM vault.decrypted_secrets
    WHERE name = 'service_role_key'
    LIMIT 1;

    IF edge_url IS NULL OR sr_key IS NULL THEN
        RAISE WARNING 'tbm_missed_attendance_check skipped: missing Vault edge URL or service role key';
        RETURN;
    END IF;

    FOR s IN
        UPDATE public.tbm_sessions
        SET missed_alert_at = NOW()
        WHERE ended_at IS NULL
          AND missed_alert_at IS NULL
          AND expected_end_at < NOW() - INTERVAL '30 minutes'
        RETURNING session_id, group_id, leader_user_id, work_scope
    LOOP
        PERFORM net.http_post(
            url := edge_url || '/functions/v1/notifications',
            headers := jsonb_build_object(
                'Content-Type', 'application/json',
                'Authorization', 'Bearer ' || sr_key
            ),
            body := jsonb_build_object(
                'action', 'tbm-missed',
                'session_id', s.session_id,
                'group_id', s.group_id,
                'leader_user_id', s.leader_user_id,
                'work_scope', s.work_scope
            )
        );
    END LOOP;
END;
$$;

DO $$
BEGIN
    PERFORM cron.unschedule('tbm_missed_attendance_minute');
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

SELECT cron.schedule(
    'tbm_missed_attendance_minute',
    '* * * * *',
    $$SELECT public.tbm_missed_attendance_check();$$
);
