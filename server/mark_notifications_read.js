const express = require('express');
const router = express.Router();
const pool = require('./db');

// 읽은 알림 삭제 처리
router.post('/mark_notifications_read', async (req, res) => {
    const { user_id, notification_id } = req.body;

    if (!user_id) {
        return res.status(400).json({ message: "사용자 ID가 필요합니다." });
    }

    try {
        let query;
        let params;

        if (notification_id) {
            // 특정 알림 읽음 -> 삭제
            query = 'DELETE FROM notifications WHERE user_id = $1 AND notification_id = $2';
            params = [user_id, notification_id];
        } else {
            // 모든 알림 읽음 -> 해당 사용자의 모든 알림 삭제
            query = 'DELETE FROM notifications WHERE user_id = $1';
            params = [user_id];
        }

        await pool.query(query, params);
        res.status(200).json({ message: "성공적으로 삭제되었습니다." });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;
