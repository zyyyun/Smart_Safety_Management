const express = require('express');
const router = express.Router();
const pool = require('./db');

// 특정 유저에게 개별 알림 전송
router.post('/send_individual_notification', async (req, res) => {
    // 클라이언트(Kotlin)에서 userId(camelCase)로 보낼 경우를 대비해 user_id와 userId 모두 확인
    const user_id = req.body.user_id || req.body.userId;
    const { title, content } = req.body;

    if (!user_id || !title || !content) {
        return res.status(400).json({ message: "필수 정보(user_id, title, content)가 누락되었습니다." });
    }

    try {
        await pool.query(
            'INSERT INTO notifications (user_id, title, content) VALUES ($1, $2, $3)',
            [user_id, title, content]
        );

        console.log(`[IndividualNotice] Sent to ${user_id}: ${title}`);
        res.status(200).json({ message: "알림이 전송되었습니다." });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;
