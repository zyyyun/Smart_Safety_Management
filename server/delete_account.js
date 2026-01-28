const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/delete_account', async (req, res) => {
    const { user_id } = req.body;

    if (!user_id) {
        return res.status(400).json({ message: "사용자 ID가 필요합니다." });
    }

    try {
        const result = await pool.query("DELETE FROM users WHERE user_id = $1", [user_id]);
        
        if (result.rowCount === 0) {
            return res.status(404).json({ message: "해당 사용자를 찾을 수 없습니다." });
        }

        res.status(200).json({ message: "회원 탈퇴가 완료되었습니다." });
    } catch (err) {
        console.error("회원 탈퇴 오류:", err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;