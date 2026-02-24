const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_geofence_zones', async (req, res) => {
    const { group_id } = req.query;

    if (!group_id) {
        return res.status(400).json({ message: "group_id가 필요합니다." });
    }

    try {
        // ST_AsGeoJSON을 사용하여 Geometry 타입을 JSON으로 변환
        const result = await pool.query(
            `SELECT zone_id, zone_name, group_id, ST_AsGeoJSON(boundary) as geojson
             FROM geofence_zones
             WHERE group_id = $1
             ORDER BY zone_id ASC`,
            [group_id]
        );

        const zones = result.rows.map(row => {
            const geojson = JSON.parse(row.geojson);
            // geojson.coordinates[0]은 [[lng, lat], ...] 형태의 배열입니다.
            const rawPoints = geojson.coordinates[0];
            
            // 마지막 점이 첫 점과 같으므로(닫힌 다각형), UI 표시를 위해 마지막 점 제거
            if (rawPoints.length > 0) {
                rawPoints.pop();
            }

            const points = rawPoints.map(coord => ({
                longitude: coord[0],
                latitude: coord[1]
            }));

            return {
                zone_id: row.zone_id,
                zone_name: row.zone_name,
                group_id: row.group_id,
                points: points
            };
        });

        res.status(200).json({ zones });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;