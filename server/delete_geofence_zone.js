const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/delete_geofence_zone', async (req, res) => {
    const { zone_id } = req.body;

    try {
        await pool.query('DELETE FROM geofence_zones WHERE zone_id = $1', [zone_id]);
        res.status(200).json({ message: "영역이 삭제되었습니다." });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;