const express = require('express');
const router = express.Router();
const pool = require('./db');
const multer = require('multer');
const path = require('path');
const fs = require('fs');

// 업로드 디렉토리 설정
const uploadDir = path.join(__dirname, 'public', 'uploads');
if (!fs.existsSync(uploadDir)) {
    fs.mkdirSync(uploadDir, { recursive: true });
}

const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        cb(null, uploadDir);
    },
    filename: (req, file, cb) => {
        cb(null, Date.now() + '-' + file.originalname);
    }
});

const upload = multer({ storage: storage });

router.post('/complete_action', upload.array('images', 5), async (req, res) => {
    const { event_id, worker_id, request_type, request_title, request_details } = req.body;
    const images = req.files;

    if (!event_id || !worker_id) {
        return res.status(400).json({ message: "필수 정보가 누락되었습니다." });
    }

    const client = await pool.connect();
    try {
        await client.query('BEGIN');

        // 1. action_requests 테이블 업데이트 (또는 삽입)
        // 이미 존재하는 요청이 있는지 확인
        const checkRes = await client.query('SELECT request_id FROM action_requests WHERE event_id = $1', [event_id]);
        
        let requestId;
        if (checkRes.rows.length > 0) {
            // 업데이트
            requestId = checkRes.rows[0].request_id;
            await client.query(
                `UPDATE action_requests 
                 SET request_type = $1, request_title = $2, request_details = $3, worker_id = $4, completed_at = NOW() 
                 WHERE request_id = $5`,
                [request_type, request_title, request_details, worker_id, requestId]
            );
        } else {
            // 삽입 (만약 관리자가 요청을 생성하지 않았는데 근로자가 완료하는 경우)
            const insertRes = await client.query(
                `INSERT INTO action_requests (event_id, worker_id, request_type, request_title, request_details, completed_at)
                 VALUES ($1, $2, $3, $4, $5, NOW()) RETURNING request_id`,
                [event_id, worker_id, request_type, request_title, request_details]
            );
            requestId = insertRes.rows[0].request_id;
        }

        // 2. 이미지 저장
        if (images && images.length > 0) {
            for (const file of images) {
                const imageUrl = `/uploads/${file.filename}`;
                await client.query('INSERT INTO action_images (request_id, image_url) VALUES ($1, $2)', [requestId, imageUrl]);
            }
        }

        // 3. detection_events 상태 업데이트
        await client.query("UPDATE detection_events SET status = 'COMPLETED' WHERE event_id = $1", [event_id]);

        await client.query('COMMIT');
        res.status(200).json({ message: "조치가 완료되었습니다." });
    } catch (err) {
        await client.query('ROLLBACK');
        console.error('Error completing action:', err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    } finally {
        client.release();
    }
});

module.exports = router;