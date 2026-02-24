const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/update_workplace', async (req, res) => {
    const { old_place_name, new_place_name, admin_id } = req.body;

    try {
        const result = await pool.query(
            'UPDATE workplace SET place_name = $1 WHERE place_name = $2 AND admin_id = $3',
            [new_place_name, old_place_name, admin_id]
        );

        if (result.rowCount > 0) {
            res.status(200).json({ message: "현장 이름이 수정되었습니다." });
        } else {
            res.status(404).json({ message: "현장을 찾을 수 없거나 권한이 없습니다." });
        }
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;