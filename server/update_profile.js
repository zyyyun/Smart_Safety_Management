const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/update_profile', async (req, res) => {
    const { user_id, phone_num, email } = req.body;

    try {
        let query = 'UPDATE users SET ';
        const values = [];
        let index = 1;
        let updates = [];

        // 전달받은 값만 업데이트 쿼리에 추가
        if (phone_num !== undefined) {
            updates.push(`phone_num = $${index++}`);
            values.push(phone_num);
        }
        if (email !== undefined) {
            updates.push(`email = $${index++}`);
            values.push(email);
        }

        if (updates.length === 0) {
            return res.status(400).json({ message: "변경할 정보가 없습니다." });
        }

        query += updates.join(', ');
        query += ` WHERE user_id = $${index}`;
        values.push(user_id);

        const result = await pool.query(query, values);

        if (result.rowCount > 0) {
            res.status(200).json({ message: "수정 완료" });
        } else {
            res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;