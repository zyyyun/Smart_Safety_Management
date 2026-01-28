const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_cctv_list', async (req, res) => {
    const { area, event_names } = req.query;

    try {
        // cameras 테이블과 camera_events, event_types를 조인하여 정보 조회
        // event_types 테이블의 event_name 컬럼을 가져온다고 가정
        let query = `
            SELECT
                c.camera_id,
                c.device_name,
                c.install_area,
                c.image_res_name,
                COALESCE(json_agg(et.event_name) FILTER (WHERE et.event_name IS NOT NULL), '[]') as events
            FROM cameras c
            LEFT JOIN camera_events ce ON c.camera_id = ce.camera_id
            LEFT JOIN event_types et ON ce.event_type_id = et.id
            WHERE 1=1
        `;

        const params = [];
        let paramIndex = 1;

        // 1. 구역 필터링 (install_area)
        if (area && area !== '전체 구역') {
            query += ` AND c.install_area LIKE $${paramIndex++}`;
            params.push(`%${area}%`);
        }

        query += ` GROUP BY c.camera_id`;

        // 2. 이벤트 필터링 (HAVING 절 사용)
        // event_names가 배열이거나 문자열일 수 있음 (Express query parser)
        let eventsFilter = [];
        if (event_names) {
            const names = Array.isArray(event_names) ? event_names : [event_names];
            eventsFilter = names.filter(e => e !== '전체');
        }

        if (eventsFilter.length > 0) {
            // 해당 그룹(카메라)의 이벤트가 필터 목록을 모두 포함하는지 확인 (AND 조건)
            query += ` HAVING array_agg(et.event_name)::text[] @> $${paramIndex++}::text[]`;
            params.push(eventsFilter);
        }

        query += ` ORDER BY c.camera_id ASC`;

        const result = await pool.query(query, params);
        res.status(200).json({ cctv_list: result.rows });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;