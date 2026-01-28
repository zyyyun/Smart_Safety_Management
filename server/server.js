const express = require('express');
const app = express();
const PORT = 3000;

// JSON 요청 본문을 파싱하기 위한 미들웨어
app.use(express.json());

// signup.js에서 정의한 라우터를 가져옴
const signupRouter = require('./signup');
const loginRouter = require('./login');
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

// '/signup' 경로에 대한 요청을 signupRouter로 전달
app.use('/', signupRouter);
app.use('/', loginRouter);
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

// 업로드된 이미지를 정적 파일로 제공 (http://서버주소/uploads/파일명 으로 접근 가능)
app.use('/uploads', express.static('public/uploads'));

app.listen(PORT, '0.0.0.0', () => {
    console.log(`서버가 http://0.0.0.0:${PORT} 에서 실행 중입니다.`);
});