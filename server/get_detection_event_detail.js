const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_detection_event_detail', async (req, res) => {
    const { event_id } = req.query;

    if (!event_id) {
        return res.status(400).json({ message: "event_id가 필요합니다." });
    }

    try {
        const query = `
            SELECT
                de.*,
                to_char(de.detected_at, 'YYYY-MM-DD HH24:MI:SS') as detected_at,
                et.event_name,
                c.image_res_name as capture_image_url
            FROM detection_events de
            LEFT JOIN cameras c ON de.camera_id = c.camera_id
            LEFT JOIN event_types et ON de.type_id = et.id
            WHERE de.event_id = $1
        `;
        const result = await pool.query(query, [event_id]);

        if (result.rows.length === 0) {
            return res.status(404).json({ message: "이벤트를 찾을 수 없습니다." });
        }

        res.status(200).json(result.rows[0]);
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;