-- ────────────────────────────────────────────────────────────────────────────
-- test_cameras.sql
-- 목적 : Next-4 검증용 카메라를 cameras 테이블에 시드
-- 전략 : 2026-04-21 기준 실기기 부재 + 공개 RTSP 데모 스트림 응답 불가.
--        해결책 : 2025 레거시(D:\2025_산업안전\산업안전\)의 AI-Hub 스타일
--        레퍼런스 영상을 로컬 파일 경로로 카메라 소스에 매핑.
--        ai_agent의 snapshot.py는 RTSP와 일반 파일 URL을 분기 처리하므로
--        로컬 파일도 FFmpeg 입력으로 그대로 사용 가능.
--
-- 전제 : test 브랜치의 test_group(group_id 환경에 따라 상이). 아래 DO 블록이
--        invite_code='TEST' → 가장 오래된 그룹 순으로 자동 매핑.
--
-- ⚠ 주의 1 : live_url_detail에 들어가는 경로는 ai_agent가 실행되는 PC 기준.
--            다른 PC에서 agent 구동 시 경로 반영 필요.
-- ⚠ 주의 2 : live_url(Android 라이브 재생용)은 null로 둠. D6 단계(ExoPlayer
--            RTSP)에서 mediamtx 등으로 재송출 시 별도 업데이트.
-- ⚠ 주의 3 : 한글·공백·괄호 포함 경로임. FFmpeg에는 그대로 전달, DB는 UTF-8.
--
-- 실행 : Supabase SQL Editor 또는 psql에서 직접 실행
-- ────────────────────────────────────────────────────────────────────────────

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

    -- 2. 레퍼런스 영상 기반 테스트 카메라 5종 INSERT
    --    각 카메라는 5종 파일럿 AI 모델(화재/안전모/쓰러짐/사람/지게차)에 대응
    INSERT INTO public.cameras (
        device_name, device_code, install_area,
        live_url, live_url_detail,
        installation_address, group_id,
        latitude, longitude, status, environment_type
    )
    VALUES
        (
            'TEST-CAM-01 (화재+안전모 레퍼런스)',
            'TEST-CAM-01',
            'A구역 1열',
            NULL,  -- Android 라이브 재생은 D6 이후 (mediamtx 재송출 시 업데이트)
            'D:/2025_산업안전/산업안전/발표자료용 영상/detection(fire, helmet).mp4',
            '인천광역시 남동구 시범지역 1',
            v_group_id,
            37.4483, 126.7316, '정상', '내부'
        ),
        (
            'TEST-CAM-02 (쓰러짐 레퍼런스)',
            'TEST-CAM-02',
            'A구역 2열',
            NULL,
            'D:/2025_산업안전/산업안전/데이터/쓰러짐 영상.mp4',
            '인천광역시 남동구 시범지역 2',
            v_group_id,
            37.4485, 126.7318, '정상', '내부'
        ),
        (
            'TEST-CAM-03 (사람 탐지 레퍼런스)',
            'TEST-CAM-03',
            'B구역 1열',
            NULL,
            'D:/2025_산업안전/산업안전/모델 7종/사람 탐지/input_video.mp4',
            '인천광역시 남동구 시범지역 3',
            v_group_id,
            37.4487, 126.7320, '정상', '내부'
        ),
        (
            'TEST-CAM-04 (지게차 레퍼런스)',
            'TEST-CAM-04',
            'B구역 2열',
            NULL,
            'D:/2025_산업안전/산업안전/모델 7종/지게차 탐지/test_forklift.gif',
            '인천광역시 남동구 시범지역 4',
            v_group_id,
            37.4489, 126.7322, '정상', '내부'
        ),
        (
            'TEST-CAM-05 (화재 단독 레퍼런스)',
            'TEST-CAM-05',
            'C구역 1열',
            NULL,
            'D:/2025_산업안전/산업안전/모델 7종/화재 탐지/input.mp4',
            '인천광역시 남동구 시범지역 5',
            v_group_id,
            37.4491, 126.7324, '정상', '내부'
        )
    ON CONFLICT (device_code) DO UPDATE SET
        live_url_detail = EXCLUDED.live_url_detail,
        install_area    = EXCLUDED.install_area,
        device_name     = EXCLUDED.device_name;

    RAISE NOTICE 'test_cameras.sql: group_id=% 에 테스트 카메라 5종 seed 완료', v_group_id;
END;
$$;

-- 시드 확인
-- SELECT camera_id, device_name, live_url_detail FROM public.cameras WHERE device_code LIKE 'TEST-CAM-%' ORDER BY device_code;
