const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/update_geofence_zone', async (req, res) => {
    const { zone_id, zone_name, points } = req.body;

    if (!zone_id || !zone_name || !points || points.length < 3) {
        return res.status(400).json({ message: "필수 정보가 누락되었습니다." });
    }

    // PostGIS POLYGON 포맷 생성
    let wktPoints = points.map(p => `${p.longitude} ${p.latitude}`).join(',');
    const firstPoint = points[0];
    wktPoints += `,${firstPoint.longitude} ${firstPoint.latitude}`;

    const wkt = `POLYGON((${wktPoints}))`;

    try {
        await pool.query(
            `UPDATE geofence_zones
             SET zone_name = $1, boundary = ST_GeomFromText($2, 4326)
             WHERE zone_id = $3`,
            [zone_name, wkt, zone_id]
        );

        res.status(200).json({ message: "영역이 수정되었습니다." });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;