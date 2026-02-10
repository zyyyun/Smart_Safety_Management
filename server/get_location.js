const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_location', async (req, res) => {
    const { user_id } = req.query;

    if (!user_id) {
        return res.status(400).json({ message: "user_id가 필요합니다." });
    }

    try {
        // 1. 요청자의 그룹 ID 조회
        const userRes = await pool.query('SELECT group_id FROM users WHERE user_id = $1', [user_id]);
        if (userRes.rows.length === 0) {
            return res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }
        const groupId = userRes.rows[0].group_id;

        // 2. 같은 그룹 내 사용자들의 최신 위치 조회
        // DISTINCT ON (u.user_id)를 사용하여 각 유저별 가장 최근 로그 하나만 가져옴
        const query = `
            SELECT DISTINCT ON (u.user_id)
                u.user_id,
                u.name,
                u.user_role as role,
                l.latitude,
                l.longitude,
                l.current_zone,
                l.status,
                l.recorded_at
            FROM users u
            LEFT JOIN location_logs l ON u.user_id = l.user_id
            WHERE u.group_id = $1
            AND EXISTS (
                SELECT 1 FROM devices d1 
                WHERE d1.user_id = u.user_id 
                AND d1.device_type ILIKE '%helmet%' 
                AND d1.gps_status = '정상'
            )
            AND EXISTS (
                SELECT 1 FROM devices d2 
                WHERE d2.user_id = u.user_id 
                AND d2.device_type ILIKE '%watch%' 
                AND d2.gps_status = '정상'
            )
            ORDER BY u.user_id, l.recorded_at DESC
        `;

        const result = await pool.query(query, [groupId]);

        res.status(200).json({ locations: result.rows });

    } catch (err) {
        console.error('Error fetching locations:', err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;