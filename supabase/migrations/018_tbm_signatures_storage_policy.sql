-- 018: Harden TBM signature storage policy
--
-- Worker TBM check-in uploads a PNG signature directly to:
--   tbm-signatures/{session_id}/{user_id}_{timestamp}.png
--
-- Migration 013 created the bucket, but its suffix guard was accidentally
-- swallowed by an inline comment. Recreate the policy explicitly.

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'tbm-signatures',
    'tbm-signatures',
    false,
    524288,
    ARRAY['image/png']
)
ON CONFLICT (id) DO UPDATE SET
    public = EXCLUDED.public,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS "tbm_signatures_insert_anon" ON storage.objects;

CREATE POLICY "tbm_signatures_insert_anon"
    ON storage.objects
    FOR INSERT
    TO anon, authenticated
    WITH CHECK (
        bucket_id = 'tbm-signatures'
        AND (storage.foldername(name))[1] ~ '^[0-9]+$'
        AND lower(right(name, 4)) = '.png'
    );

COMMENT ON POLICY "tbm_signatures_insert_anon" ON storage.objects IS
    'Allows PoC worker TBM signature PNG uploads under {session_id}/ only. No anon SELECT policy is provided.';
