const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/remove_from_group', async (req, res) => {
    const { user_id } = req.body;

    if (!user_id) {
        return res.status(400).json({ message: "user_id가 필요합니다." });
    }

    try {
        const result = await pool.query('UPDATE users SET group_id = NULL WHERE user_id = $1', [user_id]);

        if (result.rowCount > 0) {
            res.status(200).json({ message: "그룹에서 제외되었습니다." });
        } else {
            res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;