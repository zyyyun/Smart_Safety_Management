const express = require('express');
const router = express.Router();
const pool = require('./db');
const multer = require('multer');
const path = require('path');

// 이미지 저장을 위한 Multer 설정
const storage = multer.diskStorage({
    destination: function (req, file, cb) {
        cb(null, 'public/uploads/') 
    },
    filename: function (req, file, cb) {
        // 파일명 중복 방지를 위해 타임스탬프 추가
        cb(null, Date.now() + path.extname(file.originalname))
    }
});

const upload = multer({ storage: storage });

// 조치 요청 생성 API
// 라우팅 경로: /create_action_request
router.post('/create_action_request', upload.array('images', 5), async (req, res) => {
    const client = await pool.connect();
    try {
        await client.query('BEGIN');

        const { event_id, requester_id, request_type, request_title, request_details } = req.body;
        const files = req.files;

        // 1. action_requests 테이블에 요청 정보 저장
        const insertRequestQuery = `
            INSERT INTO action_requests (event_id, requester_id, request_type, request_title, request_details)
            VALUES ($1, $2, $3, $4, $5)
            RETURNING request_id
        `;
        const requestResult = await client.query(insertRequestQuery, [event_id, requester_id, request_type, request_title, request_details]);
        const requestId = requestResult.rows[0].request_id;

        // 2. action_images 테이블에 이미지 정보 저장
        if (files && files.length > 0) {
            const insertImageQuery = `INSERT INTO action_images (request_id, image_url) VALUES ($1, $2)`;
            for (const file of files) {
                // 클라이언트에서 접근 가능한 이미지 경로로 저장 (예: /uploads/filename.jpg)
                const imageUrl = `/uploads/${file.filename}`;
                await client.query(insertImageQuery, [requestId, imageUrl]);
            }
        }

        // 3. detection_events 테이블의 상태를 'REQUESTED'로 업데이트
        const updateEventQuery = `UPDATE detection_events SET status = 'REQUESTED' WHERE event_id = $1`;
        await client.query(updateEventQuery, [event_id]);

        await client.query('COMMIT');
        res.status(200).json({ message: "조치 요청이 성공적으로 등록되었습니다.", request_id: requestId });

    } catch (err) {
        await client.query('ROLLBACK');
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    } finally {
        client.release();
    }
});

module.exports = router;