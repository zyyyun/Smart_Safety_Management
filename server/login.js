const express = require('express');
const router = express.Router();
const bcrypt = require('bcrypt');
const pool = require('./db');

router.post('/login', async (req, res) => {
    const { user_id, password } = req.body;

    try {
        const result = await pool.query('SELECT * FROM users WHERE user_id = $1', [user_id]);

        if (result.rows.length === 0) {
            return res.status(401).json({ message: "존재하지 않는 아이디입니다." });
        }

        const user = result.rows[0];
        const isMatch = await bcrypt.compare(password, user.password);

        if (!isMatch) {
            return res.status(401).json({ message: "비밀번호가 일치하지 않습니다." });
        }

        res.status(200).json({
            message: "로그인 성공",
            user: {
                user_id: user.user_id,
                name: user.name,
                user_role: user.user_role,
                phone_num: user.phone_num,
                email: user.email,
                profile_image_url: user.profile_image_url,
                group_id: user.group_id ? user.group_id.toString() : null,
                invite_code: user.invite_code // DB에 저장된 코드 그대로 반환
            }
        });

    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;
