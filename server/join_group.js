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

        // 2. 관리자에게 아직 그룹 ID가 없다면 최신 그룹 번호 생성 및 'groups' 테이블에 먼저 삽입
        if (targetGroupId === null || targetGroupId === undefined) {
            const maxGroupResult = await pool.query('SELECT MAX(group_id) as max_id FROM users');
            const maxId = maxGroupResult.rows[0].max_id;
            targetGroupId = (maxId ? parseInt(maxId) : 0) + 1;
            
            // [중요] 외래키 제약조건 해결: 'groups' 테이블에 새 그룹 번호를 먼저 등록
            // 만약 groups 테이블에 name 등의 다른 필수 컬럼이 있다면 추가해야 합니다.
            try {
                await pool.query('INSERT INTO groups (group_id) VALUES ($1)', [targetGroupId]);
            } catch (groupErr) {
                // 이미 존재한다면 무시, 다른 에러라면 로그 출력
                if (groupErr.code !== '23505') console.error("Groups table insert error:", groupErr);
            }
            
            // 초대코드 소유자(관리자)에게 그룹 ID 부여
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
