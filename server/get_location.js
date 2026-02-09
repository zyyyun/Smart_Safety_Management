const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_location', async (req, res) => {
    const { user_id } = req.query; // 요청을 보낸 관리자 ID

    if (!user_id) {
        return res.status(400).json({ message: "user_id가 필요합니다." });
    }

    try {
        // 1. 요청한 관리자의 group_id 조회
        const userResult = await pool.query('SELECT group_id FROM users WHERE user_id = $1', [user_id]);

        if (userResult.rows.length === 0) {
            return res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }

        const groupId = userResult.rows[0].group_id;

        // 2. 해당 그룹의 작업자(worker) 중, 보유한 모든 기기의 GPS 상태가 'ON'인 작업자의 최신 위치 조회
        // - ValidWorkers: 기기가 1개 이상이며, 모든 기기의 gps_status가 'ON'인 작업자 필터링
        // - LatestLoc: location_logs 테이블에서 각 유저별 가장 최신(recorded_at DESC) 위치 정보 조회
        const query = `
            WITH ValidWorkers AS (
                SELECT 
                    u.user_id,
                    u.name,
                    'worker' as role
                FROM users u
                JOIN devices d ON u.user_id = d.user_id
                WHERE u.group_id = $1 
                GROUP BY u.user_id, u.name
                HAVING 
                    COUNT(*) > 0 
                    AND BOOL_AND(d.gps_status = '정상') -- 모든 기기가 '정상'이어야 함
            ),
            LatestLoc AS (
                SELECT DISTINCT ON (user_id)
                    user_id,
                    latitude,
                    longitude,
                    current_zone,
                    recorded_at
                FROM location_logs
                ORDER BY user_id, recorded_at DESC
            )
            SELECT 
                vw.user_id,
                vw.name,
                vw.role,
                ll.latitude,
                ll.longitude,
                ll.current_zone,
                ll.recorded_at
            FROM ValidWorkers vw
            LEFT JOIN LatestLoc ll ON vw.user_id = ll.user_id
        `;

        const result = await pool.query(query, [groupId]);

        res.status(200).json({ locations: result.rows });

    } catch (err) {
        console.error('Error in get_location:', err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;