const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_workplace_location', async (req, res) => {
    const { user_id } = req.query;

    if (!user_id) {
        return res.status(400).json({ message: "user_id가 필요합니다." });
    }

    try {
        // workplace 테이블에서 해당 유저(관리자)의 현장 위치 조회
        const result = await pool.query(
            'SELECT address, road_address FROM workplace WHERE admin_id = $1',
            [user_id]
        );

        if (result.rows.length === 0) {
            return res.status(404).json({ message: "등록된 현장 위치가 없습니다." });
        }

        res.status(200).json(result.rows[0]);

    } catch (err) {
        console.error('Error fetching workplace location:', err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;