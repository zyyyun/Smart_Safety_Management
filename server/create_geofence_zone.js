const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/create_geofence_zone', async (req, res) => {
    const { group_id, zone_name, points } = req.body;

    if (!group_id || !zone_name || !points || points.length < 3) {
        return res.status(400).json({ message: "필수 정보가 누락되었습니다." });
    }

    // PostGIS POLYGON 포맷 생성: POLYGON((lng lat, lng lat, ...))
    // PostGIS는 (Longitude, Latitude) 순서입니다.
    // 다각형은 닫혀있어야 하므로 첫 번째 점을 마지막에 다시 추가합니다.
    let wktPoints = points.map(p => `${p.longitude} ${p.latitude}`).join(',');
    const firstPoint = points[0];
    wktPoints += `,${firstPoint.longitude} ${firstPoint.latitude}`;

    const wkt = `POLYGON((${wktPoints}))`;

    try {
        const result = await pool.query(
            `INSERT INTO geofence_zones (group_id, zone_name, boundary)
             VALUES ($1, $2, ST_GeomFromText($3, 4326))
             RETURNING zone_id`,
            [group_id, zone_name, wkt]
        );

        res.status(200).json({
            message: "영역이 생성되었습니다.",
            zone_id: result.rows[0].zone_id
        });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;