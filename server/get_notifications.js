const express = require('express');
const router = express.Router();
const pool = require('./db');

// 사용자의 알림 리스트 가져오기
router.get('/get_notifications', async (req, res) => {
    const { user_id } = req.query;

    if (!user_id) {
        return res.status(400).json({ message: "사용자 ID가 필요합니다." });
    }

    try {
        const query = `
            SELECT 
                notification_id, 
                title, 
                content, 
                created_at, 
                is_read 
            FROM notifications 
            WHERE user_id = $1 
            ORDER BY created_at DESC
        `;
        const result = await pool.query(query, [user_id]);

        res.status(200).json({
            notifications: result.rows
        });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;
