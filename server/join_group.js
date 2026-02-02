const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/join_group', async (req, res) => {
    const { user_id, invite_code } = req.body;

    try {
        // 1. 초대코드를 소유한 관리자 찾기
        const ownerResult = await pool.query(
            'SELECT user_id, group_id FROM users WHERE invite_code = $1', 
            [invite_code]
        );

        if (ownerResult.rows.length === 0) {
            return res.status(404).json({ message: "유효하지 않은 초대코드입니다." });
        }

        const owner = ownerResult.rows[0];
        let targetGroupId = owner.group_id;

        // 2. 관리자에게 아직 그룹 ID가 없다면 최신 그룹 번호(MAX + 1) 생성 및 부여
        if (targetGroupId === null || targetGroupId === undefined) {
            const maxGroupResult = await pool.query('SELECT MAX(group_id) as max_id FROM users');
            const maxId = maxGroupResult.rows[0].max_id;
            targetGroupId = (maxId ? parseInt(maxId) : 0) + 1;
            
            // 초대코드 소유자(관리자)에게도 그룹 ID 부여
            await pool.query('UPDATE users SET group_id = $1 WHERE user_id = $2', [targetGroupId, owner.user_id]);
        }

        // 3. 요청한 사용자(입력자)에게 동일한 그룹 ID 부여
        await pool.query(
            'UPDATE users SET group_id = $1 WHERE user_id = $2', 
            [targetGroupId, user_id]
        );

        res.status(200).json({ 
            message: "그룹에 성공적으로 참여했습니다.",
            group_id: targetGroupId.toString()
        });

    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;
