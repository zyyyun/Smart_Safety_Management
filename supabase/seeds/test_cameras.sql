-- ────────────────────────────────────────────────────────────────────────────
-- test_cameras.sql
-- 목적 : Next-4 검증용 더미 카메라를 cameras 테이블에 시드
-- 전제 : test 브랜치의 test_group(group_id 환경에 따라 상이). 아래 SELECT로 group_id
--        를 자동 조회해 매핑. 그룹이 없으면 대신 invite_code='TEST'로 찾는다.
-- 실행 : Supabase SQL Editor 또는 psql에서 직접 실행
-- ────────────────────────────────────────────────────────────────────────────

-- ⚠ 공개 RTSP 데모 스트림 — 가용성에 따라 한시적. 실패 시 주석 내 대체 URL 사용.

DO $$
DECLARE
    v_group_id INTEGER;
BEGIN
    -- 1. test 세션용 그룹 찾기 (우선순위 : invite_code='TEST' → 가장 오래된 그룹)
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

    -- 2. 테스트 카메라 2개 INSERT (이미 존재하면 skip)
    INSERT INTO public.cameras (
        device_name, device_code, install_area,
        live_url, live_url_detail,
        installation_address, group_id,
        latitude, longitude, status, environment_type
    )
    VALUES
        (
            'TEST-CAM-01 (Big Buck Bunny)',
            'TEST-CAM-01',
            'A구역 1열',
            'rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mp4',
            'rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mp4',
            '인천광역시 남동구 시범지역 1',
            v_group_id,
            37.4483, 126.7316, '정상', '내부'
        ),
        (
            'TEST-CAM-02 (Zephyr demo)',
            'TEST-CAM-02',
            'A구역 2열',
            'rtsp://zephyr.rtsp.stream/movie?streamKey=feedf2c3c7a1a07e2c4b24bd4b74b6b2',
            'rtsp://zephyr.rtsp.stream/movie?streamKey=feedf2c3c7a1a07e2c4b24bd4b74b6b2',
            '인천광역시 남동구 시범지역 2',
            v_group_id,
            37.4485, 126.7318, '정상', '내부'
        )
    ON CONFLICT (device_code) DO NOTHING;

    RAISE NOTICE 'test_cameras.sql: group_id=% 에 테스트 카메라 seed 완료', v_group_id;
END;
$$;

-- 시드 확인
-- SELECT camera_id, device_name, live_url_detail FROM public.cameras WHERE device_code LIKE 'TEST-CAM-%';
