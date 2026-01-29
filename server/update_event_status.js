const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/update_event_status', async (req, res) => {
    const { event_id, status } = req.body;

    if (!event_id || !status) {
        return res.status(400).json({ message: "event_id와 status가 필요합니다." });
    }

    try {
        const query = 'UPDATE detection_events SET status = $1 WHERE event_id = $2';
        const result = await pool.query(query, [status, event_id]);

        if (result.rowCount === 0) {
            return res.status(404).json({ message: "이벤트를 찾을 수 없습니다." });
        }

        res.status(200).json({ message: "상태가 업데이트되었습니다." });
    } catch (err) {
        console.error("이벤트 상태 업데이트 오류:", err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;