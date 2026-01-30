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

// 업로드된 이미지를 정적 파일로 제공 (http://서버주소/uploads/파일명 으로 접근 가능)
app.use('/uploads', express.static(path.join(__dirname, 'public', 'uploads')));

app.listen(PORT, '0.0.0.0', () => {
    console.log(`서버가 ${PORT} 포트에서 실행 중입니다.`);
});
