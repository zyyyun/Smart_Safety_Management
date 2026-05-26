# 12-02 Summary - Notifications Edge Function

Executed at: 2026-05-26 16:14 +09:00

## Completed

- Updated `supabase/functions/notifications/index.ts`.
- `tbm-start` now requires `work_scope`, validates active OPS templates, snapshots hazards/controls, inserts checklist rows from `checks`, and includes `work_scope` in FCM data.
- `tbm-end` now accepts `key_hazard_id` and `feedback_notes`.
- `tbm-missed` includes `work_scope` in the push payload.
- Added manager-only OPS actions:
  - `ops-create`
  - `ops-update`
  - `ops-toggle`
- Updated TBM smoke scripts for v2 payload shape:
  - `tests/smoke/tbm_start.sh`
  - `tests/smoke/tbm_end.sh`

## Verification

- Android build consumed the new notification payload fields successfully.
- Full Edge Function deploy/smoke was not run locally.

## Pending Manual Step

- Deploy the Edge Function after DB migration:
  - `supabase functions deploy notifications`
- Then run the TBM smoke flow against the remote project.
