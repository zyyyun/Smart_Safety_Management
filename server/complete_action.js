const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/complete_action', async (req, res) => {
    const { event_id, worker_id } = req.body;

    if (!event_id || !worker_id) {
        return res.status(400).json({ message: "event_id와 worker_id가 필요합니다." });
    }

    const client = await pool.connect();

    try {
        await client.query('BEGIN');

        // 1. action_requests 테이블에 완료자 ID와 완료 시간 업데이트
        const updateActionRequestQuery = `
            UPDATE action_requests 
            SET worker_id = $1, completed_at = NOW() 
            WHERE event_id = $2
        `;
        await client.query(updateActionRequestQuery, [worker_id, event_id]);

        // 2. detection_events 테이블의 status를 'COMPLETED'로 업데이트
        const updateEventStatusQuery = `
            UPDATE detection_events 
            SET status = 'COMPLETED' 
            WHERE event_id = $1
        `;
        await client.query(updateEventStatusQuery, [event_id]);

        await client.query('COMMIT');
        res.status(200).json({ message: "조치 완료 처리되었습니다." });

    } catch (err) {
        await client.query('ROLLBACK');
        console.error('Error completing action:', err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    } finally {
        client.release();
    }
});

module.exports = router;