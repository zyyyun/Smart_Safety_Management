const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_fire_detectors', async (req, res) => {
    try {
        const result = await pool.query(
            `SELECT detector_id, detector_name, is_active, status, 
             to_char(last_update, 'YYYY-MM-DD HH24:MI:SS') as last_update 
             FROM fire_detectors 
             ORDER BY detector_id ASC`
        );
        res.status(200).json({ fire_detectors: result.rows });
    } catch (err) {
        console.error('Error fetching fire detectors:', err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;