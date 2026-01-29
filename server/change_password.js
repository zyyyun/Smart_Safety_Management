const express = require('express');
const router = express.Router();
const bcrypt = require('bcrypt');
const pool = require('./db');

router.post('/change_password', async (req, res) => {
    const { user_id, new_password } = req.body;

    try {
        // 1. 새 비밀번호 암호화
        const hashedPassword = await bcrypt.hash(new_password, 10);

        // 2. DB 업데이트
        const result = await pool.query(
            'UPDATE users SET password = $1 WHERE user_id = $2',
            [hashedPassword, user_id]
        );

        if (result.rowCount > 0) {
            res.status(200).json({ message: "비밀번호 변경 성공" });
        } else {
            res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;