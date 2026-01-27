const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_cctv_list', async (req, res) => {
    try {
        // cameras 테이블과 camera_events, event_types를 조인하여 정보 조회
        // event_types 테이블의 event_name 컬럼을 가져온다고 가정
        const query = `
            SELECT
                c.camera_id,
                c.device_name,
                c.install_area,
                c.image_res_name,
                COALESCE(json_agg(et.event_name) FILTER (WHERE et.event_name IS NOT NULL), '[]') as events
            FROM cameras c
            LEFT JOIN camera_events ce ON c.camera_id = ce.camera_id
            LEFT JOIN event_types et ON ce.event_type_id = et.id
            GROUP BY c.camera_id
            ORDER BY c.camera_id ASC
        `;
        const result = await pool.query(query);
        res.status(200).json({ cctv_list: result.rows });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;