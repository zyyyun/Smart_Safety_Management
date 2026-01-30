const express = require('express');
const router = express.Router();
const pool = require('./db'); // pg pool 사용

router.post('/find_id', async (req, res) => {
    let { name, phone_num } = req.body;

    if (!name || !phone_num) {
        return res.status(400).json({ message: "이름과 전화번호를 모두 제공해야 합니다." });
    }

    // 입력받은 전화번호에서 하이픈(-) 제거 (숫자만 남김)
    const cleanPhoneNum = phone_num.replace(/-/g, '');

    try {
        // DB의 phone_num에서 하이픈을 제거한 값과 입력받은 cleanPhoneNum을 비교
        const query = "SELECT user_id FROM users WHERE name = $1 AND REPLACE(phone_num, '-', '') = $2";
        const result = await pool.query(query, [name, cleanPhoneNum]);

        if (result.rows.length > 0) {
            const userId = result.rows[0].user_id;
            return res.status(200).json({
                message: "사용자를 찾았습니다.",
                user_id: userId
            });
        } else {
            return res.status(404).json({ message: "일치하는 사용자를 찾을 수 없습니다." });
        }
    } catch (err) {
        console.error("데이터베이스 쿼리 오류:", err);
        return res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;
