const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_user_info', async (req, res) => {
    const { user_id } = req.query;

    if (!user_id) {
        return res.status(400).json({ message: "user_id가 필요합니다." });
    }

    try {
        const result = await pool.query(
            'SELECT user_id, name, phone_num, email, user_role, group_id, is_invite_checked, invite_code, profile_image_url FROM users WHERE user_id = $1',
            [user_id]
        );

        if (result.rows.length === 0) {
            return res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }

        const user = result.rows[0];
        res.status(200).json({
            user_id: user.user_id,
            name: user.name,
            phone_num: user.phone_num,
            email: user.email,
            user_role: user.user_role,
            group_id: user.group_id,
            is_invite_checked: user.is_invite_checked,
            invite_code: user.invite_code,
            profile_image_url: user.profile_image_url
        });

    } catch (err) {
        console.error('Error fetching user info:', err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;