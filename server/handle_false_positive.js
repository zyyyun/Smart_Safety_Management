const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/handle_false_positive', async (req, res) => {
    const { event_id, user_id } = req.body;

    if (!event_id || !user_id) {
        return res.status(400).json({ message: "event_id와 user_id가 필요합니다." });
    }

    const client = await pool.connect();

    try {
        await client.query('BEGIN');

        // 1. action_requests 테이블에 오탐처리 데이터 추가
        // worker_id와 requester_id를 user_id(관리자)로 설정
        // request_type, request_title, request_details를 '오탐처리'로 설정
        // completed_at을 현재 시간으로 설정 (즉시 완료 처리)
        const insertQuery = `
            INSERT INTO action_requests (event_id, requester_id, worker_id, request_type, request_title, request_details, completed_at)
            VALUES ($1, $2, $2, '오탐처리', '오탐처리', '오탐처리', NOW())
        `;
        await client.query(insertQuery, [event_id, user_id]);

        // 2. detection_events 테이블의 status를 'FALSE_POSITIVE'로 업데이트
        const updateQuery = `
            UPDATE detection_events 
            SET status = 'FALSE_POSITIVE' 
            WHERE event_id = $1
        `;
        await client.query(updateQuery, [event_id]);

        await client.query('COMMIT');
        res.status(200).json({ message: "오탐 처리가 완료되었습니다." });

    } catch (err) {
        await client.query('ROLLBACK');
        console.error('Error handling false positive:', err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    } finally {
        client.release();
    }
});

module.exports = router;