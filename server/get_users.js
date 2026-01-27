const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_users', async (req, res) => {
    try {
        // 모든 사용자 조회 (비밀번호 제외)
        const result = await pool.query('SELECT user_id, name, phone_num, user_role, email FROM users');
        res.status(200).json({ users: result.rows });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;