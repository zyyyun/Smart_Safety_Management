const express = require('express');
const router = express.Router();
const pool = require('./db');

// 특정 그룹의 모든 근로자에게 알림 전송
router.post('/send_group_notification', async (req, res) => {
    const { sender_id, title, content } = req.body;

    if (!sender_id || !title || !content) {
        return res.status(400).json({ message: "필수 정보가 누락되었습니다." });
    }

    try {
        // 1. 발신자(관리자)의 group_id 조회
        const senderResult = await pool.query('SELECT group_id FROM users WHERE user_id = $1', [sender_id]);
        
        if (senderResult.rows.length === 0 || !senderResult.rows[0].group_id) {
            return res.status(404).json({ message: "그룹 정보를 찾을 수 없습니다." });
        }

        const groupId = senderResult.rows[0].group_id;

        // 2. 같은 그룹의 모든 근로자(worker) 조회
        const workersResult = await pool.query(
            "SELECT user_id FROM users WHERE group_id = $1 AND user_role = 'worker'",
            [groupId]
        );

        if (workersResult.rows.length === 0) {
            return res.status(200).json({ message: "알림을 보낼 근로자가 없습니다." });
        }

        // 3. 모든 근로자에게 알림 삽입 (Bulk Insert)
        const insertPromises = workersResult.rows.map(worker => {
            return pool.query(
                'INSERT INTO notifications (user_id, title, content) VALUES ($1, $2, $3)',
                [worker.user_id, title, content]
            );
        });

        await Promise.all(insertPromises);

        console.log(`[GroupNotification] Sent to ${workersResult.rows.length} workers in group ${groupId}`);
        res.status(200).json({ message: "알림이 성공적으로 전송되었습니다." });

    } catch (err) {
        console.error(`[GroupNotification] Error:`, err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;
