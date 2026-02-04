const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_users', async (req, res) => {
    // 클라이언트가 user_id 또는 userId로 보낼 수 있으므로 둘 다 확인
    const user_id = req.query.user_id || req.query.userId;

    if (!user_id) {
        return res.status(400).json({ message: "user_id가 필요합니다." });
    }

    try {
        // 1. 요청한 유저의 group_id 조회
        const userResult = await pool.query('SELECT group_id FROM users WHERE user_id = $1', [user_id]);

        if (userResult.rows.length === 0) {
            return res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }

        const groupId = userResult.rows[0].group_id;

        if (!groupId) {
            return res.status(200).json({ users: [] });
        }

        // 2. 같은 group_id를 가진 유저 조회 (비밀번호 제외)
        const result = await pool.query(
            `SELECT user_id, name, phone_num, user_role, email, is_invite_checked 
             FROM users 
             WHERE group_id = $1 
             ORDER BY CASE WHEN user_role = 'manager' THEN 0 ELSE 1 END, name ASC`,
            [groupId]
        );
        res.status(200).json({ users: result.rows });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;