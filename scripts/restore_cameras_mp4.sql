-- Phase 8 plan 04 검증 후 cameras 원복 — Phase 1 의 mp4 storage URL 로 복귀.
-- 본 SQL 은 plan 08-04 Task 2 의 임시 UPDATE 를 되돌림.
-- 미실행 시 다음 정상 detection cycle 가 mediamtx 꺼져있을 때 SnapshotError 폭주
-- → 5분 후 cameras_healthcheck() FCM 폭주 위험.
--
-- 사용: PostgREST 또는 Dashboard SQL Editor 에서 실행.
-- 검증: SELECT camera_id, live_url_detail FROM cameras WHERE camera_id IN (1,5);

UPDATE public.cameras
SET live_url_detail = 'https://xbjqxnvemcqubjfflain.supabase.co/storage/v1/object/public/reference-videos/fire/source_v2.mp4'
WHERE camera_id = 1;

UPDATE public.cameras
SET live_url_detail = 'https://xbjqxnvemcqubjfflain.supabase.co/storage/v1/object/public/reference-videos/helmet/source_v2.mp4'
WHERE camera_id = 5;

-- 검증 후 health_state / last_frame_at 도 reset (선택, 다음 cron tick 이 자연 회복)
-- UPDATE public.cameras SET health_state='unknown', last_frame_at=NULL, last_alert_at=NULL WHERE camera_id IN (1,5);
