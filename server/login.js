const express = require('express');
const router = express.Router();
const bcrypt = require('bcrypt');
const pool = require('./db');

router.post('/login', async (req, res) => {
    const { user_id, password } = req.body;

    try {
        // 1. 아이디로 사용자 조회
        const result = await pool.query('SELECT * FROM users WHERE user_id = $1', [user_id]);

        // 사용자가 없는 경우
        if (result.rows.length === 0) {
            return res.status(401).json({ message: "존재하지 않는 아이디입니다." });
        }

        const user = result.rows[0];

        // 2. 비밀번호 비교 (입력받은 값 vs DB에 저장된 해시값)
        const isMatch = await bcrypt.compare(password, user.password);

        if (!isMatch) {
            return res.status(401).json({ message: "비밀번호가 일치하지 않습니다." });
        }

        // 3. 로그인 성공 응답 (필요한 사용자 정보 반환)
        res.status(200).json({
            message: "로그인 성공",
            user: {
                user_id: user.user_id,
                name: user.name,
                user_role: user.user_role,
                phone_num: user.phone_num,
                email: user.email
            }
        });

    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;