const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_detection_events', async (req, res) => {
    const { user_id } = req.query;

    if (!user_id) {
        return res.status(400).json({ message: "user_id가 필요합니다." });
    }

    try {
        // 1. 요청한 유저의 group_id 조회
        const userResult = await pool.query('SELECT group_id FROM users WHERE user_id = $1', [user_id]);

        if (userResult.rows.length === 0) {
            return res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }

        const groupId = userResult.rows[0].group_id;

        if (!groupId) {
             return res.status(200).json({ events: [] });
        }

        // 2. 해당 그룹의 카메라에서 발생한 이벤트 조회
        const query = `
            SELECT
                de.event_id, de.risk_level, de.install_area, de.device_name, de.accuracy, de.status,
                to_char(de.detected_at, 'YYYY-MM-DD HH24:MI:SS') as detected_at,
                et.event_name
            FROM detection_events de
            JOIN cameras c ON de.camera_id = c.camera_id
            LEFT JOIN event_types et ON de.type_id = et.id
            WHERE c.group_id = $1
            ORDER BY de.detected_at DESC
        `;

        const result = await pool.query(query, [groupId]);
        res.status(200).json({ events: result.rows });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;