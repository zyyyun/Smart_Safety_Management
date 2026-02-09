const express = require('express');
const fs = require('fs');
const path = require('path');
const app = express();
const PORT = 3000;

// 업로드 디렉토리 자동 생성 (public/uploads)
const uploadDir = path.join(__dirname, 'public', 'uploads');
if (!fs.existsSync(uploadDir)) {
    fs.mkdirSync(uploadDir, { recursive: true });
}

// JSON 요청 본문을 파싱하기 위한 미들웨어
app.use(express.json());

// 각 기능별 라우터를 가져옴
const signupRouter = require('./signup');
const loginRouter = require('./login');
const findUserRouter = require('./find_user'); // 추가
const updateProfileRouter = require('./update_profile');
const changePasswordRouter = require('./change_password');
const createWorkplaceRouter = require('./create_workplace');
const deleteWorkplaceRouter = require('./delete_workplace');
const checkRegisteredContactsRouter = require('./check_registered_contacts');
const getUsersRouter = require('./get_users');
const removeFromGroupRouter = require('./remove_from_group');
const getCCTVListRouter = require('./get_cctv_list');
const uploadImageRouter = require('./upload_image');
const getCCTVDetailRouter = require('./get_cctv_detail');
const getCCTVStreamInfoRouter = require('./get_cctv_stream_info'); // ✅ 추가
const getDeviceStatusRouter = require('./get_device_status');
const getWorkerDeviceStatusRouter = require('./get_worker_device_status');
const getDetectionEventsRouter = require('./get_detection_events');
const getDetectionEventDetailRouter = require('./get_detection_event_detail');
const getWorkplaceRouter = require('./get_workplace');
const deleteAccountRouter = require('./delete_account');
const createActionRequestRouter = require('./create_action_request');
const updateEventStatusRouter = require('./update_event_status');
const verificationRouter = require('./verification'); // 추가
const completeActionRouter = require('./complete_action');
const handleFalsePositiveRouter = require('./handle_false_positive');
const getDailyChecksRouter = require('./get_daily_checks');
const createDailyCheckRouter = require('./create_daily_check');
const updateDailyCheckRouter = require('./update_daily_check');
const deleteDailyCheckRouter = require('./delete_daily_check');
const completeDailyCheckRouter = require('./complete_daily_check'); // ✅ 추가
const getNotificationsRouter = require('./get_notifications');
const markNotificationsReadRouter = require('./mark_notifications_read');
const joinGroupRouter = require('./join_group'); // 추가
const getGroupMembersRouter = require('./get_group_members'); // 추가: 그룹 멤버 조회
const getEventTypesRouter = require('./get_event_types'); // 추가: 이벤트 유형 조회
const sendGroupNotificationRouter = require('./send_group_notification'); // 추가
const sendIndividualNotificationRouter = require('./send_individual_notification'); // 추가
const registerWorkplaceLocationRouter = require('./register_workplace_location'); // 추가: 현장 위치 등록
const getWorkplaceLocationRouter = require('./get_workplace_location'); // 추가: 현장 위치 조회
const deleteCamerasRouter = require('./delete_cameras'); // ✅ 추가: 카메라 삭제
const getUserInfoRouter = require('./get_user_info'); // ✅ 추가: 유저 정보 조회
const checkInviteAvailabilityRouter = require('./check_invite_availability'); // ✅ 추가: 초대 가능 여부 확인
const inviteMembersRouter = require('./invite_members'); // ✅ 추가: 멤버 초대 등록
const getPendingInvitesRouter = require('./get_pending_invites'); // ✅ 추가: 대기중인 초대 목록 조회
const cancelInviteRouter = require('./cancel_invite'); // ✅ 추가: 초대 취소
const startCronJobs = require('./cron_scheduler'); // ✅ 추가: 스케줄러 모듈
const getCameraCapturesRouter = require('./get_camera_captures'); // ✅ 추가: 카메라 캡처 조회
const createAiEventRouter = require('./create_ai_event'); // ✅ 추가: AI 이벤트 생성
const getLocationRouter = require('./get_location'); // ✅ 추가: 작업자 위치 조회

// 라우터 등록
app.use('/', signupRouter);
app.use('/', loginRouter);
app.use('/', findUserRouter); // 추가
app.use('/', updateProfileRouter);
app.use('/', changePasswordRouter);
app.use('/', createWorkplaceRouter);
app.use('/', deleteWorkplaceRouter);
app.use('/', checkRegisteredContactsRouter);
app.use('/', getUsersRouter);
app.use('/', removeFromGroupRouter);
app.use('/', getCCTVListRouter);
app.use('/', uploadImageRouter);
app.use('/', getCCTVDetailRouter);
app.use('/', getCCTVStreamInfoRouter); // ✅ 추가
app.use('/', getDeviceStatusRouter);
app.use('/', getWorkerDeviceStatusRouter);
app.use('/', getDetectionEventsRouter);
app.use('/', getDetectionEventDetailRouter);
app.use('/', getWorkplaceRouter);
app.use('/', deleteAccountRouter);
app.use('/', createActionRequestRouter);
app.use('/', updateEventStatusRouter);
app.use('/', verificationRouter); // 추가
app.use('/', completeActionRouter);
app.use('/', handleFalsePositiveRouter);
app.use('/', getDailyChecksRouter);
app.use('/', createDailyCheckRouter);
app.use('/', updateDailyCheckRouter);
app.use('/', deleteDailyCheckRouter);
app.use('/', completeDailyCheckRouter); // ✅ 추가
app.use('/', getNotificationsRouter);
app.use('/', markNotificationsReadRouter);
app.use('/', joinGroupRouter); // 추가
app.use('/', getGroupMembersRouter); // 추가
app.use('/', getEventTypesRouter); // 추가
app.use('/', sendGroupNotificationRouter); // 추가
app.use('/', sendIndividualNotificationRouter); // 추가
app.use('/', registerWorkplaceLocationRouter); // 추가
app.use('/', getWorkplaceLocationRouter); // 추가
app.use('/', deleteCamerasRouter); // ✅ 추가
app.use('/', getUserInfoRouter); // ✅ 추가
app.use('/', checkInviteAvailabilityRouter); // ✅ 추가
app.use('/', inviteMembersRouter); // ✅ 추가
app.use('/', getPendingInvitesRouter); // ✅ 추가
app.use('/', cancelInviteRouter); // ✅ 추가
app.use('/', getCameraCapturesRouter); // ✅ 추가
app.use('/', createAiEventRouter); // ✅ 추가
app.use('/', getLocationRouter); // ✅ 추가

// 업로드된 이미지를 정적 파일로 제공 (http://서버주소/uploads/파일명 으로 접근 가능)
app.use('/uploads', express.static(path.join(__dirname, 'public', 'uploads')));

// ✅ [디버깅] 404 핸들러 추가: 라우터 매칭 실패 시 요청된 경로를 로그로 출력
app.use((req, res, next) => {
    console.log(`⚠️ [404 Not Found] 요청된 경로: ${req.method} ${req.url}`);
    res.status(404).json({ message: `경로를 찾을 수 없습니다: ${req.method} ${req.url}` });
});

// ✅ 스케줄러 시작 (20분마다 스냅샷 촬영)
startCronJobs();

app.listen(PORT, '0.0.0.0', () => {
    console.log(`서버가 ${PORT} 포트에서 실행 중입니다.`);
});
