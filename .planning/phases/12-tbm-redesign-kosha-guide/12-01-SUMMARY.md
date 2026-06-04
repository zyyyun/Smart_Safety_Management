# 12-01 Summary - TBM v2 Schema

Executed at: 2026-05-26 16:14 +09:00

## Completed

- Added `supabase/migrations/017_tbm_v2_schema.sql`.
- Recreates the four TBM tables with `work_scope`, hazard/control snapshots, `key_hazard_id`, and `feedback_notes`.
- Seeds three plating-domain OPS templates: `forklift`, `chemical`, `hot_work`.
- Keeps `tbm_templates` SELECT-only for anon/authenticated; OPS writes are intended to go through the Edge Function.
- Re-registers TBM tables in `supabase_realtime` and recreates `tbm_missed_attendance_check()`.
- Added `tests/sql/test_017_tbm_v2_isolation.sql` with 8 assertions.

## Verification

- Local static checks passed:
  - migration file exists in the correct `017` slot.
  - no hardcoded JWT-like `eyJ` token found in the migration/function files.
  - existing `014/015/016` migration slots remain untouched.

## Pending Manual Step

- `supabase db push --linked --yes` was not run because this migration is destructive (`DROP TABLE ... CASCADE` for TBM tables).
- `tests/sql/test_017_tbm_v2_isolation.sql` still needs to be run against the linked Supabase DB after explicit approval/apply.
