-- ────────────────────────────────────────────────────────────────────────────
-- test_cameras.sql
-- 목적 : Next-4 검증용 카메라를 cameras 테이블에 시드
-- 전략 : 실기기 부재 + 공개 RTSP 데모 응답 불가 상황에서 reference_media/에
--        사용자가 모아두는 "실제 현장 영상"을 카메라 소스로 매핑.
--        ai_agent는 live_url_detail이 NULL/빈 문자열이 아닌 행만 처리하므로
--        아직 영상을 못 구한 카테고리는 live_url_detail=NULL로 두면 자동 스킵.
--
-- 현재 보유 현황 (2026-04-21)
--   ✅ fall (쓰러짐)   : reference_media/fall/E02_001.mp4
--   ⏳ fire (화재)      : 영상 수급 대기
--   ⏳ helmet (안전모)   : 영상 수급 대기
--   ⏳ person (사람)     : 영상 수급 대기
--   ⏳ forklift (지게차) : 영상 수급 대기
--
-- 영상 추가 시 이 파일의 해당 카메라 live_url_detail만 업데이트하고
-- `supabase db query --linked -f supabase/seeds/test_cameras.sql` 재실행.
--
-- ⚠ 경로는 ai_agent가 실행되는 PC 기준. 다른 PC에서 구동 시 재매핑 필요.
-- ⚠ live_url(Android 라이브 재생)은 NULL로 유지. D6(ExoPlayer RTSP) 단계에서
--    mediamtx 등으로 재송출 시 업데이트.
-- ────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_group_id INTEGER;
BEGIN
    -- 1. test 세션용 그룹 찾기
    SELECT group_id INTO v_group_id
    FROM public.groups
    WHERE invite_code = 'TEST'
    LIMIT 1;

    IF v_group_id IS NULL THEN
        SELECT group_id INTO v_group_id
        FROM public.groups
        ORDER BY created_at ASC
        LIMIT 1;
    END IF;

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'test_cameras.sql: groups 테이블에 그룹이 없습니다. 먼저 회원가입/그룹 생성을 완료하세요.';
    END IF;

    -- 2. 레퍼런스 카메라 5종 UPSERT
    --    live_url_detail이 NULL이면 agent가 스킵 → 영상 수급 전까지 안전하게 비워둠
    INSERT INTO public.cameras (
        device_name, device_code, install_area,
        live_url, live_url_detail,
        installation_address, group_id,
        latitude, longitude, status, environment_type
    )
    VALUES
        (
            'TEST-CAM-01 (화재 레퍼런스)',
            'TEST-CAM-01',
            'A구역 1열',
            NULL,
            NULL,  -- 영상 수급 후 reference_media/fire/*.mp4 경로 지정
            '인천광역시 남동구 시범지역 1',
            v_group_id,
            37.4483, 126.7316, '정상', '내부'
        ),
        (
            'TEST-CAM-02 (쓰러짐 레퍼런스)',
            'TEST-CAM-02',
            'A구역 2열',
            NULL,
            'D:/2026_산업안전/Smart_Safety_Management/reference_media/fall/E02_001.mp4',
            '인천광역시 남동구 시범지역 2',
            v_group_id,
            37.4485, 126.7318, '정상', '내부'
        ),
        (
            'TEST-CAM-03 (사람 레퍼런스)',
            'TEST-CAM-03',
            'B구역 1열',
            NULL,
            NULL,  -- 영상 수급 후 reference_media/person/*.mp4 경로 지정
            '인천광역시 남동구 시범지역 3',
            v_group_id,
            37.4487, 126.7320, '정상', '내부'
        ),
        (
            'TEST-CAM-04 (지게차 레퍼런스)',
            'TEST-CAM-04',
            'B구역 2열',
            NULL,
            NULL,  -- 영상 수급 후 reference_media/forklift/*.mp4 경로 지정
            '인천광역시 남동구 시범지역 4',
            v_group_id,
            37.4489, 126.7322, '정상', '내부'
        ),
        (
            'TEST-CAM-05 (안전모 레퍼런스)',
            'TEST-CAM-05',
            'C구역 1열',
            NULL,
            NULL,  -- 영상 수급 후 reference_media/helmet/*.mp4 경로 지정
            '인천광역시 남동구 시범지역 5',
            v_group_id,
            37.4491, 126.7324, '정상', '내부'
        )
    ON CONFLICT (device_code) DO UPDATE SET
        device_name     = EXCLUDED.device_name,
        install_area    = EXCLUDED.install_area,
        live_url        = EXCLUDED.live_url,
        live_url_detail = EXCLUDED.live_url_detail;

    RAISE NOTICE 'test_cameras.sql: group_id=% 에 카메라 5종 upsert 완료 (활성 1개)', v_group_id;
END;
$$;

-- 적용 확인
-- SELECT camera_id, device_code, device_name,
--        CASE WHEN live_url_detail IS NULL THEN '(영상 미수급)' ELSE live_url_detail END AS source
-- FROM public.cameras WHERE device_code LIKE 'TEST-CAM-%' ORDER BY device_code;
