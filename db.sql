1.AI감지이벤트에 근로자가 작성하는 조치요청과 관련된 테이블
----------------------------------------------------

CREATE TABLE IF NOT EXISTS public.action_images  -- 조치요청에 첨부되는 이미지 관리 테이블
(
    image_id integer NOT NULL DEFAULT nextval('action_images_image_id_seq'::regclass), -- 이미지 key값
    request_id integer NOT NULL,  -- 조치요청 게시글의 key값
    image_url text COLLATE pg_catalog."default" NOT NULL, -- 이미지 URL
    CONSTRAINT action_images_pkey PRIMARY KEY (image_id), 
    CONSTRAINT fk_request FOREIGN KEY (request_id)
        REFERENCES public.action_requests (request_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
)

CREATE TABLE IF NOT EXISTS public.action_requests -- 조치요청 게시글 테이블
(
    request_id integer NOT NULL DEFAULT nextval('action_requests_request_id_seq'::regclass),
    event_id integer NOT NULL, -- AI 감지 이벤트 key값 
    requester_id character varying(50) COLLATE pg_catalog."default", --요청자 (관리자) id
    worker_id character varying(50) COLLATE pg_catalog."default", --조치자 (근로자) id
    request_type text COLLATE pg_catalog."default" NOT NULL, -- 조치공유/조치필요/즉시조치 3가지 (시안 그대로)
    request_title text COLLATE pg_catalog."default" NOT NULL,  -- 제목
    request_details text COLLATE pg_catalog."default" NOT NULL, --내용
    requested_at timestamp without time zone DEFAULT now(), --요청 날짜
    action_report text COLLATE pg_catalog."default", -- 조치 후 점검 내용(근로자가 작성)
    completed_at timestamp without time zone, --조치 완료 날짜
    CONSTRAINT action_requests_pkey PRIMARY KEY (request_id),
    CONSTRAINT fk_event FOREIGN KEY (event_id)
        REFERENCES public.detection_events (event_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
)

2.이벤트 발생 및 카메라와 연관된 테이블
----------------------------------

CREATE TABLE IF NOT EXISTS public.camera_captures 
(
    capture_id integer NOT NULL DEFAULT nextval('camera_captures_capture_id_seq'::regclass),
    camera_id integer,  --카메라 key값 
    image_url text COLLATE pg_catalog."default" NOT NULL, --이미지 url
    captured_at timestamp without time zone DEFAULT now(), --캡처된 날짜
    event_type character varying(50) COLLATE pg_catalog."default", -- 주기적인 캡처(periodic), 이벤트 감지(DETECTION) 
    CONSTRAINT camera_captures_pkey PRIMARY KEY (capture_id),
    CONSTRAINT camera_captures_camera_id_fkey FOREIGN KEY (camera_id)
        REFERENCES public.cameras (camera_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
)

CREATE TABLE IF NOT EXISTS public.camera_events --카메라가 어떤 이벤트의 특화되어있는지 카테고리.
(
    camera_id integer NOT NULL, --카메라 key값
    event_type_id integer NOT NULL, --이벤트 타입 key값 ( ex : 쓰러짐, 화재사고, 협착사고 ...)
    CONSTRAINT camera_events_pkey PRIMARY KEY (camera_id, event_type_id),
    CONSTRAINT camera_events_camera_id_fkey FOREIGN KEY (camera_id)
        REFERENCES public.cameras (camera_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE,
    CONSTRAINT camera_events_event_type_id_fkey FOREIGN KEY (event_type_id)
        REFERENCES public.event_types (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
)

CREATE TABLE IF NOT EXISTS public.cameras --카메라 테이블
(
    camera_id integer NOT NULL DEFAULT nextval('cameras_camera_id_seq'::regclass),
    device_name character varying(100) COLLATE pg_catalog."default" NOT NULL, --기기이름
    device_code character varying(50) COLLATE pg_catalog."default", --기기 코드
    host_code character varying(50) COLLATE pg_catalog."default", --호스트 코드
    host_id character varying(50) COLLATE pg_catalog."default", --호스트 id
    host_password character varying(255) COLLATE pg_catalog."default", --호스트 비밀번호
    last_comm_date timestamp without time zone, --마지막 통신 날짜
    status character varying(20) COLLATE pg_catalog."default", --상태 (정상, 미수신?)
    install_area character varying(100) COLLATE pg_catalog."default", --설치구역 시안 예시 : C구역 1열)
    direction character varying(50) COLLATE pg_catalog."default", --감지 영역 ( 12시 3시 ...)
    shooting_interval integer,  --감지 주기 ex : 5분 10분 
    created_at timestamp without time zone DEFAULT now(), --생성 날짜.
    operating_hours character varying(24) COLLATE pg_catalog."default" DEFAULT '000000000000000000000000'::character varying,  -- 감지시간?을 비트식으로. 010000000000... 이면 1시에만 감지.
    image_res_name character varying(50) COLLATE pg_catalog."default", --이미지 url링크
    group_id integer, --어느 그룹 카메라인지 그룹id
    live_url text COLLATE pg_catalog."default", -- 실시간 url 1
    installation_address character varying(255) COLLATE pg_catalog."default", --설치 주소(도로명주소)
    environment_type character varying(20) COLLATE pg_catalog."default" DEFAULT '내부'::character varying, --내부,도로
    live_url_detail text COLLATE pg_catalog."default", -- 실시간 url 2
    latitude double precision, --설치 주소의 위도
    longitude double precision, -- 설치 주소의 경도
    CONSTRAINT cameras_pkey PRIMARY KEY (camera_id),
    CONSTRAINT cameras_device_code_key UNIQUE (device_code),
    CONSTRAINT fk_group FOREIGN KEY (group_id)
        REFERENCES public.groups (group_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE SET NULL
)

3.일일안전점검과 관련된 테이블
--------------------------

CREATE TABLE IF NOT EXISTS public.check_images --일일 안전점검에 첨부하는 사진 테이블
(
    image_id integer NOT NULL DEFAULT nextval('check_images_image_id_seq'::regclass),
    check_id integer NOT NULL, --일일안전점검 게시글 key값 
    image_url text COLLATE pg_catalog."default" NOT NULL, --이미지 url
    CONSTRAINT check_images_pkey PRIMARY KEY (image_id),
    CONSTRAINT fk_check FOREIGN KEY (check_id)
        REFERENCES public.daily_safety_check (check_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
)

CREATE TABLE IF NOT EXISTS public.daily_safety_check --일일안전점검 게시글 테이블
(
    check_id integer NOT NULL DEFAULT nextval('daily_safety_check_check_id_seq'::regclass),
    writer_id character varying(50) COLLATE pg_catalog."default" NOT NULL, --관리자id
    worker_id character varying(50) COLLATE pg_catalog."default",   --근로자 id
    location character varying(255) COLLATE pg_catalog."default" NOT NULL, --점검위치 ( ex: C구역 1열)
    hazard text COLLATE pg_catalog."default", --위험요인
    countermeasure text COLLATE pg_catalog."default", --위험대책
    status character varying(20) COLLATE pg_catalog."default" DEFAULT '미점검'::character varying, --미점검,점검완료
    check_date date, --점검날짜.
    created_at date DEFAULT CURRENT_DATE, --생성날짜
    check_content text COLLATE pg_catalog."default", --점검내용
    CONSTRAINT daily_safety_check_pkey PRIMARY KEY (check_id),
    CONSTRAINT fk_writer FOREIGN KEY (writer_id)
        REFERENCES public.users (user_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE,
    CONSTRAINT daily_safety_check_status_check CHECK (status::text = ANY (ARRAY['미점검'::character varying, '점검완료'::character varying]::text[]))
)


4.AI이벤트 관련 테이블
--------------------

CREATE TABLE IF NOT EXISTS public.detection_events --감지된 이벤트 정보 테이블 이 정보로 게시글도 작성
(
    event_id integer NOT NULL DEFAULT nextval('detection_events_event_id_seq'::regclass),
    camera_id integer, --감지한 카메라 key값
    device_name character varying(100) COLLATE pg_catalog."default", --카메라의 device_name
    install_area character varying(100) COLLATE pg_catalog."default", --카메라의 install_area
    installation_address character varying(255) COLLATE pg_catalog."default", --카메라의 installaion_address
    live_url text COLLATE pg_catalog."default", --카메라의 live_url
    accuracy double precision, --정확도
    status character varying(20) COLLATE pg_catalog."default" DEFAULT 'PENDING'::character varying, 
    --상태 PENDING:조치대기 REQUESTED:요청중 FALSE_POSITIVE:오탐처리 COMPLETED: 조치완료
    detected_at timestamp without time zone DEFAULT now(), --감지된 날짜
    risk_level character varying(20) COLLATE pg_catalog."default" DEFAULT NULL::character varying,
    --위험단계 DANGER(위험),WARNING(경고),CAUTION(주의)
    type_id integer, --어떤 이벤트가 감지되었는지에 대한 유형의 key값
    capture_id integer, --위 카메라가 캡처한 url 테이블의 특정 key값
   
    CONSTRAINT detection_events_pkey PRIMARY KEY (event_id),
    CONSTRAINT detection_events_camera_id_fkey FOREIGN KEY (camera_id)
        REFERENCES public.cameras (camera_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT detection_events_capture_id_fkey FOREIGN KEY (capture_id)
        REFERENCES public.camera_captures (capture_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE SET NULL,
    CONSTRAINT fk_event_type FOREIGN KEY (type_id)
        REFERENCES public.event_types (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE SET NULL
)

CREATE TABLE IF NOT EXISTS public.event_types 특정 사고와 번호를 매핑한 테이블.
(
    id integer NOT NULL DEFAULT nextval('event_types_id_seq'::regclass),
    event_name character varying(50) COLLATE pg_catalog."default" NOT NULL,
    -- 1에 쓰러짐이 매핑되어있다면 아이디가 1번이면 그 사건은 쓰러짐이 발생한 사건.
    CONSTRAINT event_types_pkey PRIMARY KEY (id),
    CONSTRAINT event_types_event_name_key UNIQUE (event_name)
)

5.유저의 기기와 관련된 테이블
----------------------------

CREATE TABLE IF NOT EXISTS public.devices --스마트기기들의 정보들중 공통된 정보만.
(
    device_id integer NOT NULL DEFAULT nextval('devices_device_id_seq'::regclass),
    device_type character varying(20) COLLATE pg_catalog."default" NOT NULL, --watch, helmet
    serial_number character varying(50) COLLATE pg_catalog."default" NOT NULL, --기기 시리얼번호
    battery_level integer DEFAULT 100, --기기의 배터리 정보
    gps_status character varying(20) COLLATE pg_catalog."default" DEFAULT 'OFF'::character varying, --GPS 상태
    user_id character varying(50) COLLATE pg_catalog."default", --user_id 값
    updated_at timestamp without time zone DEFAULT now(), --등록날짜.
    CONSTRAINT devices_pkey PRIMARY KEY (device_id),
    CONSTRAINT devices_serial_number_key UNIQUE (serial_number),
    CONSTRAINT devices_user_id_fkey FOREIGN KEY (user_id)
        REFERENCES public.users (user_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE SET NULL
)

CREATE TABLE IF NOT EXISTS public.device_watches --스마트워치 정보
(
    device_id integer NOT NULL, -- 기기 key값 
    body_temp double precision DEFAULT 36.5, -- 온도 저장
    heart_rate integer DEFAULT 70, -- 심박수 저장
    CONSTRAINT device_watches_pkey PRIMARY KEY (device_id),
    CONSTRAINT device_watches_device_id_fkey FOREIGN KEY (device_id)
        REFERENCES public.devices (device_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
)

CREATE TABLE IF NOT EXISTS public.device_helmets --스마트헬멧 정보
(
    device_id integer NOT NULL, --기기 key값
    unworn_count integer DEFAULT 0, --미착용 횟수
    CONSTRAINT device_helmets_pkey PRIMARY KEY (device_id),
    CONSTRAINT device_helmets_device_id_fkey FOREIGN KEY (device_id)
        REFERENCES public.devices (device_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
)

6.알림 관련 테이블
-----------------

CREATE TABLE IF NOT EXISTS public.notifications --알림 테이블
(
    notification_id integer NOT NULL DEFAULT nextval('notifications_notification_id_seq'::regclass),
    user_id character varying(50) COLLATE pg_catalog."default" NOT NULL, --받은 user_id
    title character varying(100) COLLATE pg_catalog."default" NOT NULL, -- 제목
    content text COLLATE pg_catalog."default" NOT NULL, --내용
    is_read boolean DEFAULT false, -- 읽었는지 확인여부 읽으면 true가 되면서 알아서 행을 삭제시킴.
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP, --생성된 날짜
    CONSTRAINT notifications_pkey PRIMARY KEY (notification_id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id)
        REFERENCES public.users (user_id) MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
)

7.유저와 그룹 관련 테이블
------------------------

CREATE TABLE IF NOT EXISTS public.users
(
    user_key integer NOT NULL DEFAULT nextval('users_user_key_seq'::regclass),
    user_id character varying(50) COLLATE pg_catalog."default" NOT NULL, -- 유저 아이디
    password character varying(255) COLLATE pg_catalog."default" NOT NULL, -- 해싱된 비밀번호
    phone_num character varying(20) COLLATE pg_catalog."default", --핸드폰 번호
    email character varying(100) COLLATE pg_catalog."default", --이메일
    name character varying(50) COLLATE pg_catalog."default", --이름
    user_role character varying(20) COLLATE pg_catalog."default" DEFAULT 'worker'::character varying, 
    --역할 관리자, 근로자
    created_at timestamp without time zone DEFAULT now(), --생성날짜
    group_id integer, -- 그룹id 기본 null / 초대코드 입력시 해당 그룹 id 배정
    profile_image_url character varying(255) COLLATE pg_catalog."default", --프로필 이미지 url
    is_invite_checked boolean DEFAULT false, --그룹 초대 여부
    invite_code character varying(13) COLLATE pg_catalog."default", --초대코드(계정마다 랜덤하게 생성)
    CONSTRAINT users_pkey PRIMARY KEY (user_key),
    CONSTRAINT users_invite_code_key UNIQUE (invite_code),
    CONSTRAINT users_user_id_key UNIQUE (user_id),
    CONSTRAINT fk_user_group FOREIGN KEY (group_id)
        REFERENCES public.groups (group_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE SET NULL
)

CREATE TABLE IF NOT EXISTS public.groups 
(
    group_id integer NOT NULL DEFAULT nextval('groups_group_id_seq'::regclass),
    invite_code character varying(20) COLLATE pg_catalog."default" NOT NULL, --유저의 초대코드
    manager_id character varying(50) COLLATE pg_catalog."default" NOT NULL,  --그룹 관리자 id
    created_at timestamp without time zone DEFAULT now(), --생성날짜
    CONSTRAINT groups_pkey PRIMARY KEY (group_id),
    CONSTRAINT groups_invite_code_key UNIQUE (invite_code),
    CONSTRAINT fk_group_manager FOREIGN KEY (manager_id)
        REFERENCES public.users (user_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
)

CREATE TABLE IF NOT EXISTS public.group_members --초대코드 발송과 관련된 테이블
(

    group_id integer NOT NULL,
    user_id character varying(50) COLLATE pg_catalog."default", --발송한 관리자 id
    phone_number character varying(20) COLLATE pg_catalog."default" NOT NULL, --초대코드를 받는 번호
    member_status character varying(20) COLLATE pg_catalog."default" DEFAULT 'PENDING'::character varying,
    -- 가입안했으면 PENDING, 가입했으면 ACTIVE
    joined_at timestamp without time zone DEFAULT now(), --가입날짜
    invite_code character varying(20) COLLATE pg_catalog."default", --초대코드
    invitee_name character varying(50) COLLATE pg_catalog."default", --초대받은 사람 이름
    invited_role character varying(20) COLLATE pg_catalog."default", --초대받은 사람의 역할
    CONSTRAINT group_members_pkey PRIMARY KEY (group_id, phone_number),
    CONSTRAINT fk_member_group FOREIGN KEY (group_id)
        REFERENCES public.groups (group_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE,
    CONSTRAINT fk_member_user FOREIGN KEY (user_id)
        REFERENCES public.users (user_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE SET NULL,
    CONSTRAINT check_member_status CHECK (member_status::text = ANY (ARRAY['PENDING'::text, 'ACTIVE'::text, 'CANCELED'::text]))
)

8.위치정보와 관련된 테이블
------------------------

CREATE TABLE IF NOT EXISTS public.location_logs
(
    log_id integer NOT NULL DEFAULT nextval('location_logs_log_id_seq'::regclass),
    user_id character varying(50) COLLATE pg_catalog."default", --근로자 id
    latitude double precision NOT NULL, --GPS로 받은 위도
    longitude double precision NOT NULL, --GPS로 받은 경도
    current_zone character varying(100) COLLATE pg_catalog."default", --GPS나 카메라로 감지된 최근 위치
    camera_id integer, --만약 카메라가 감지했다면 해당 카메라 ID
    recorded_at timestamp without time zone DEFAULT now(), --기록된 시간
    status character varying(20) COLLATE pg_catalog."default" DEFAULT '정상'::character varying,
    -- 정상이었다가 고열 혹은 쓰러짐이 발생하면 해당 키워드로 변경해서 위치 정보에 출력.
    CONSTRAINT location_logs_pkey PRIMARY KEY (log_id),
    CONSTRAINT location_logs_camera_id_fkey FOREIGN KEY (camera_id)
        REFERENCES public.cameras (camera_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT location_logs_user_id_fkey FOREIGN KEY (user_id)
        REFERENCES public.users (user_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
    
)


CREATE TABLE IF NOT EXISTS public.workplace 설정->현장 생성 및 현장 위치 설정
(
    place_id integer NOT NULL DEFAULT nextval('workplace_place_id_seq'::regclass),
    place_name character varying(100) COLLATE pg_catalog."default" NOT NULL, --현장이름
    address character varying(255) COLLATE pg_catalog."default", --주소
    road_address character varying(255) COLLATE pg_catalog."default", --도로명주소
    admin_id character varying(50) COLLATE pg_catalog."default" NOT NULL, --담당관리자 ID
    latitude double precision, --주소의 위도
    longitude double precision, --주소의 경도
    CONSTRAINT workplace_pkey PRIMARY KEY (place_id),
    CONSTRAINT fk_admin FOREIGN KEY (admin_id)
        REFERENCES public.users (user_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
)





