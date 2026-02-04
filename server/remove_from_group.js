const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/remove_from_group', async (req, res) => {
    const { user_id } = req.body;

    if (!user_id) {
        return res.status(400).json({ message: "제외할 사용자의 ID가 필요합니다." });
    }

    try {
        // 1. 사용자의 현재 그룹 ID 조회
        const userResult = await pool.query('SELECT group_id, phone_num FROM users WHERE user_id = $1', [user_id]);
        if (userResult.rows.length === 0) {
            return res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }
        const groupId = userResult.rows[0].group_id;
        const phoneNum = userResult.rows[0].phone_num;

        if (!groupId) {
            return res.status(200).json({ message: "사용자가 이미 그룹에 속해있지 않습니다." });
        }

        // 2. 해당 그룹의 관리자인지 확인
        const groupResult = await pool.query('SELECT manager_id FROM groups WHERE group_id = $1', [groupId]);
        if (groupResult.rows.length > 0 && groupResult.rows[0].manager_id === user_id) {
            return res.status(403).json({ message: "관리자는 그룹에서 제외할 수 없습니다." });
        }

        // 3. 사용자의 group_id를 null로, is_invite_checked를 false로 업데이트
        await pool.query(
            'UPDATE users SET group_id = NULL, is_invite_checked = false WHERE user_id = $1',
            [user_id]
        );

        // 4. group_members 테이블에서도 삭제 (초대 상태 초기화)
        if (phoneNum) {
            await pool.query(
                'DELETE FROM group_members WHERE group_id = $1 AND phone_number = $2',
                [groupId, phoneNum]
            );
        }

        res.status(200).json({ message: "사용자를 그룹에서 성공적으로 제외했습니다." });

    } catch (err) {
        console.error('Error removing user from group:', err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;