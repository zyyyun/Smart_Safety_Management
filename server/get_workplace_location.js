const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_workplace_location', async (req, res) => {
    const { user_id } = req.query;

    try {
        const result = await pool.query('SELECT address, road_address FROM workplace WHERE admin_id = $1', [user_id]);

        if (result.rows.length > 0) {
            res.status(200).json(result.rows[0]);
        } else {
            res.status(404).json({ message: "위치 정보 없음" });
        }
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류" });
    }
});

module.exports = router;