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
                de.event_id, 
                de.risk_level, 
                de.install_area, 
                de.device_name, 
                de.accuracy, 
                de.status,
                to_char(de.detected_at, 'YYYY-MM-DD HH24:MI:SS') as detected_at,
                et.event_name,
                c.installation_address, 
                c.live_url,
                c.latitude,
                c.longitude,
                ar.request_type, 
                ar.request_title, 
                ar.request_details,
                (SELECT COALESCE(json_agg(image_url), '[]') FROM action_images WHERE request_id = ar.request_id) as action_images,
                cc.image_url as capture_image_url,
                cc.capture_id
            FROM detection_events de
            JOIN cameras c ON de.camera_id = c.camera_id
            LEFT JOIN event_types et ON de.type_id = et.id
            LEFT JOIN action_requests ar ON de.event_id = ar.event_id
            LEFT JOIN camera_captures cc ON de.capture_id = cc.capture_id
            WHERE de.event_id = $1
        `;

        const result = await pool.query(query, [event_id]);

        if (result.rows.length === 0) {
            return res.status(404).json({ message: "이벤트를 찾을 수 없습니다." });
        }

        const event = result.rows[0];

        // 이미지 URL에 호스트 주소 추가 (상대 경로인 경우)
        if (event.capture_image_url && !event.capture_image_url.startsWith('http')) {
            event.capture_image_url = `${req.protocol}://${req.get('host')}${event.capture_image_url}`;
        }

        res.status(200).json(event);
    } catch (err) {
        console.error('Error fetching detection event detail:', err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;