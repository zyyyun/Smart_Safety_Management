const express = require('express');
const router = express.Router();
const pool = require('./db');

// ✅ 내부 및 외부에서 사용할 알림 전송 함수
const sendStatusAlarm = async (client, user_id, status, current_zone) => {
    // 정상이면 알림 보내지 않음
    if (!status || status === '정상') return;

    try {
        // 1. current_zone이 없으면 최신 로그에서 조회
        if (!current_zone) {
            const logRes = await client.query(
                "SELECT current_zone FROM location_logs WHERE user_id = $1 ORDER BY recorded_at DESC LIMIT 1",
                [user_id]
            );
            if (logRes.rows.length > 0) {
                current_zone = logRes.rows[0].current_zone;
            }
        }
        current_zone = current_zone || '위치 미상';

        // 2. 사용자의 그룹 ID 조회
        const userRes = await client.query('SELECT group_id, name FROM users WHERE user_id = $1', [user_id]);
        if (userRes.rows.length === 0) return;
        const groupId = userRes.rows[0].group_id;
        const userName = userRes.rows[0].name;

        // 3. 해당 그룹의 관리자 목록 조회
        const managersRes = await client.query(
            "SELECT user_id FROM users WHERE group_id = $1 AND user_role = 'manager'",
            [groupId]
        );

        // 4. 알림 전송 (DB 저장)
        const title = "안전감시단";
        const content = `[${userName}] ${current_zone} ${status} 발생`;

        for (const manager of managersRes.rows) {
            await client.query(
                'INSERT INTO notifications (user_id, title, content, created_at, is_read) VALUES ($1, $2, $3, NOW(), false)',
                [manager.user_id, title, content]
            );
        }
        console.log(`[Alarm Sent] ${content} to ${managersRes.rows.length} managers.`);

    } catch (err) {
        console.error('Error in sendStatusAlarm:', err);
    }
};

// 라우터 객체에 함수를 붙여서 export (다른 파일에서 require로 사용 가능)
router.sendStatusAlarm = sendStatusAlarm;

module.exports = router;