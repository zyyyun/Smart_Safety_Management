const express = require('express');
const router = express.Router();
const db = require('./db');

router.post('/find_id', (req, res) => {
    const { name, phone_num } = req.body;

    if (!name || !phone_num) {
        return res.status(400).json({ message: "이름과 전화번호를 모두 제공해야 합니다." });
    }

    // 데이터베이스에서 이름과 전화번호가 일치하는 유저 검색
    const query = "SELECT user_id FROM users WHERE name = ? AND phone_num = ?";
    db.query(query, [name, phone_num], (err, results) => {
        if (err) {
            console.error("데이터베이스 쿼리 오류:", err);
            return res.status(500).json({ message: "서버 오류가 발생했습니다." });
        }

        if (results.length > 0) {
            const userId = results[0].user_id;
            return res.status(200).json({
                message: "사용자를 찾았습니다.",
                user_id: userId
            });
        } else {
            return res.status(404).json({ message: "일치하는 사용자를 찾을 수 없습니다." });
        }
    });
});

module.exports = router;
