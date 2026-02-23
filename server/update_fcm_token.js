const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/update_fcm_token', async (req, res) => {
    const { user_id, fcm_token } = req.body;

    if (!user_id || !fcm_token) {
        return res.status(400).json({ message: "user_id와 fcm_token이 필요합니다." });
    }

    try {
        await pool.query(
            'UPDATE users SET fcm_token = $1 WHERE user_id = $2',
            [fcm_token, user_id]
        );

        res.status(200).json({ message: "FCM 토큰이 업데이트되었습니다." });
    } catch (err) {
        console.error("FCM 토큰 업데이트 오류:", err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;