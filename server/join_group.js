const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/join_group', async (req, res) => {
    const { user_id, invite_code } = req.body;

    console.log(`[JoinGroup] Request received - User: ${user_id}, Code: ${invite_code}`);

    try {
        // 1. 초대코드를 소유한 관리자 찾기
        const ownerResult = await pool.query(
            'SELECT user_id, group_id, name FROM users WHERE invite_code = $1', 
            [invite_code]
        );

        if (ownerResult.rows.length === 0) {
            console.log(`[JoinGroup] Invalid code: ${invite_code}`);
            return res.status(404).json({ message: "유효하지 않은 초대코드입니다." });
        }

        const owner = ownerResult.rows[0];
        let targetGroupId = owner.group_id;

        console.log(`[JoinGroup] Code owner found: ${owner.user_id}, Current Group: ${targetGroupId}`);

        // 2. 관리자에게 아직 그룹 ID가 없다면 새로운 그룹 ID 생성
        if (targetGroupId === null || targetGroupId === undefined) {
            const maxGroupResult = await pool.query('SELECT MAX(group_id) as max_id FROM users');
            const maxId = maxGroupResult.rows[0].max_id;
            targetGroupId = (maxId ? parseInt(maxId) : 0) + 1;
            
            console.log(`[JoinGroup] Creating new group ID: ${targetGroupId}`);
            
            // 초대코드 소유자(관리자)에게 그룹 ID 부여
            await pool.query('UPDATE users SET group_id = $1 WHERE user_id = $2', [targetGroupId, owner.user_id]);
        }

        // 3. 요청한 사용자에게 그룹 ID 부여
        // (is_invite_checked 컬럼이 없을 수도 있으므로 제외하고 group_id만 업데이트)
        const updateResult = await pool.query(
            'UPDATE users SET group_id = $1 WHERE user_id = $2', 
            [targetGroupId, user_id]
        );

        if (updateResult.rowCount === 0) {
            console.log(`[JoinGroup] User not found: ${user_id}`);
            return res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }

        console.log(`[JoinGroup] Success! User ${user_id} joined group ${targetGroupId}`);

        res.status(200).json({ 
            message: "그룹에 성공적으로 참여했습니다.",
            group_id: targetGroupId.toString()
        });

    } catch (err) {
        console.error(`[JoinGroup] Error:`, err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;
