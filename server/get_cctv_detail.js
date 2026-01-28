const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_cctv_detail', async (req, res) => {
    const { camera_id } = req.query;

    if (!camera_id) {
        return res.status(400).json({ message: "camera_id가 필요합니다." });
    }

    try {
        const query = `
            SELECT
                c.*,
                COALESCE(json_agg(et.event_name) FILTER (WHERE et.event_name IS NOT NULL), '[]') as events
            FROM cameras c
            LEFT JOIN camera_events ce ON c.camera_id = ce.camera_id
            LEFT JOIN event_types et ON ce.event_type_id = et.id
            WHERE c.camera_id = $1
            GROUP BY c.camera_id
        `;
        const result = await pool.query(query, [camera_id]);

        if (result.rows.length === 0) {
            return res.status(404).json({ message: "카메라를 찾을 수 없습니다." });
        }

        res.status(200).json(result.rows[0]);
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;