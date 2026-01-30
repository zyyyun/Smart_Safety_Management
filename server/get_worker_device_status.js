const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_worker_device_status', async (req, res) => {
    const { user_id } = req.query;

    if (!user_id) {
        return res.status(400).json({ message: "user_id가 필요합니다." });
    }

    try {
        const query = `
            SELECT
                d.device_type,
                d.battery_level,
                dh.unworn_count,
                dw.body_temp,
                dw.heart_rate
            FROM devices d
            LEFT JOIN device_helmets dh ON d.device_id = dh.device_id
            LEFT JOIN device_watches dw ON d.device_id = dw.device_id
            WHERE d.user_id = $1
        `;

        const result = await pool.query(query, [user_id]);
        res.status(200).json({ devices: result.rows });

    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;