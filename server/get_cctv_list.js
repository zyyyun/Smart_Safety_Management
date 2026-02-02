const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_cctv_list', async (req, res) => {
    const { user_id } = req.query;

    if (!user_id) {
        return res.status(400).json({ message: "user_id가 필요합니다." });
    }

    try {
        // 1. 사용자의 group_id 조회
        const userResult = await pool.query('SELECT group_id FROM users WHERE user_id = $1', [user_id]);

        if (userResult.rows.length === 0) {
            return res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }

        const groupId = userResult.rows[0].group_id;

        // 2. CCTV 리스트 조회
        // ✅ environment_type 컬럼을 추가하여 DB 값을 가져옵니다.
        const query = `
            SELECT 
                c.camera_id, 
                c.device_name, 
                c.install_area, 
                c.image_res_name, 
                c.environment_type, 
                (
                    SELECT COALESCE(json_agg(et.event_name), '[]')
                    FROM camera_events ce
                    JOIN event_types et ON ce.event_type_id = et.id
                    WHERE ce.camera_id = c.camera_id
                ) as events
            FROM cameras c
            WHERE c.group_id = $1
            ORDER BY c.camera_id ASC
        `;

        const result = await pool.query(query, [groupId]);
        res.status(200).json({ cctv_list: result.rows });

    } catch (err) {
        console.error('Error fetching CCTV list:', err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;