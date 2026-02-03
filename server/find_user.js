const express = require('express');
const router = express.Router();
const pool = require('./db'); // pg pool 사용

console.log("✅ find_user.js 라우터가 로드되었습니다.");

// 1. 아이디 찾기 API
router.post('/find_id', async (req, res) => {
    const name = req.body.name;
    // 클라이언트가 phoneNum(camelCase)으로 보낼 경우 대비
    const phone_num = req.body.phone_num || req.body.phoneNum;

    if (!name || !phone_num) {
        return res.status(400).json({ message: "이름과 전화번호를 모두 제공해야 합니다." });
    }

    const cleanPhoneNum = phone_num.replace(/-/g, '');

    try {
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
        console.error("아이디 찾기 쿼리 오류:", err);
        return res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

// 2. 비밀번호 찾기 전 사용자 확인 API (추가됨)
router.post('/verify_user_for_password', async (req, res) => {
    const name = req.body.name;
    // 클라이언트가 id 또는 userId, phoneNum으로 보낼 경우 대비
    const user_id = req.body.user_id || req.body.id || req.body.userId;
    const phone_num = req.body.phone_num || req.body.phoneNum;

    if (!name || !user_id || !phone_num) {
        return res.status(400).json({ message: "이름, 아이디, 전화번호를 모두 입력해야 합니다." });
    }

    const cleanPhoneNum = phone_num.replace(/-/g, '');

    try {
        // 이름, 아이디, 전화번호(하이픈 제거 비교)가 모두 일치하는 유저가 있는지 확인
        const query = "SELECT * FROM users WHERE name = $1 AND user_id = $2 AND REPLACE(phone_num, '-', '') = $3";
        const result = await pool.query(query, [name, user_id, cleanPhoneNum]);

        if (result.rows.length > 0) {
            return res.status(200).json({ exists: true });
        } else {
            return res.status(200).json({ exists: false });
        }
    } catch (err) {
        console.error("비밀번호 찾기 유저 확인 오류:", err);
        return res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;
