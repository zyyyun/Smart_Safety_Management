const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_geofence_zones', async (req, res) => {
    const { group_id } = req.query;

    if (!group_id) {
        return res.status(400).json({ message: "group_id가 필요합니다." });
    }

    try {
        // ✅ [최적화] DB 쿼리 내에서 JSON 객체 배열을 직접 생성하여 Node.js의 연산 부하 제거
        // 1. ST_DumpPoints: 다각형의 좌표들을 개별 점으로 분해
        // 2. WHERE (dp.path)[2] < ST_NPoints(...): 닫힌 다각형의 마지막 중복 점(첫 점과 동일)을 제외
        // 3. json_build_object & json_agg: 위도/경도 객체 배열로 변환
        const query = `
            SELECT 
                z.zone_id, 
                z.zone_name, 
                z.group_id,
                COALESCE(
                    (
                        SELECT json_agg(
                            json_build_object('latitude', ST_Y(dp.geom), 'longitude', ST_X(dp.geom))
                        )
                        FROM ST_DumpPoints(ST_ExteriorRing(z.boundary)) dp
                        WHERE (dp.path)[1] < ST_NPoints(ST_ExteriorRing(z.boundary))
                    ),
                    '[]'::json
                ) AS points
            FROM geofence_zones z
            WHERE z.group_id = $1
            ORDER BY z.zone_id ASC
        `;

        const result = await pool.query(query, [group_id]);
        res.status(200).json({ zones: result.rows });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;