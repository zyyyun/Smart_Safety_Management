const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/create_workplace', async (req, res) => {
    const { place_name, admin_id } = req.body;

    try {
        // address와 road_address는 입력받지 않았으므로 생략 (NULL로 저장됨)
        const result = await pool.query(
            'INSERT INTO workplace (place_name, admin_id) VALUES ($1, $2) RETURNING *',
            [place_name, admin_id]
        );

        res.status(201).json({ message: "현장 생성 성공", workplace: result.rows[0] });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;