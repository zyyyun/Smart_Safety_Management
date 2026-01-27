const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/delete_workplace', async (req, res) => {
    const { place_name, admin_id } = req.body;

    try {
        const result = await pool.query(
            'DELETE FROM workplace WHERE place_name = $1 AND admin_id = $2',
            [place_name, admin_id]
        );

        if (result.rowCount > 0) {
            res.status(200).json({ message: "현장 삭제 성공" });
        } else {
            res.status(404).json({ message: "현장을 찾을 수 없거나 권한이 없습니다." });
        }
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;