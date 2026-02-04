const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/invite_members', async (req, res) => {
    const { sender_id, contacts } = req.body;

    // 필수 데이터 검증
    if (!sender_id || !contacts || !Array.isArray(contacts) || contacts.length === 0) {
        return res.status(400).json({ message: "필수 정보가 누락되었습니다." });
    }

    const client = await pool.connect();
    try {
        await client.query('BEGIN');

        // 1. 요청자(관리자)의 group_id 조회
        const userRes = await client.query('SELECT group_id FROM users WHERE user_id = $1', [sender_id]);
        if (userRes.rows.length === 0 || !userRes.rows[0].group_id) {
            await client.query('ROLLBACK');
            return res.status(404).json({ message: "그룹 정보를 찾을 수 없습니다." });
        }
        const groupId = userRes.rows[0].group_id;

        // 2. 그룹의 초대 코드(invite_code) 조회
        let inviteCode = null;
        
        // 우선 groups 테이블 확인
        try {
            const groupRes = await client.query('SELECT invite_code FROM groups WHERE group_id = $1', [groupId]);
            if (groupRes.rows.length > 0) {
                inviteCode = groupRes.rows[0].invite_code;
            }
        } catch (err) {
            // groups 테이블이 없거나 쿼리 실패 시 무시
        }

        // groups 테이블에서 못 찾았을 경우, 요청자(관리자)의 정보에서 확인
        if (!inviteCode) {
            const userInviteRes = await client.query('SELECT invite_code FROM users WHERE user_id = $1', [sender_id]);
            if (userInviteRes.rows.length > 0) {
                inviteCode = userInviteRes.rows[0].invite_code;
            }
        }

        if (!inviteCode) {
            await client.query('ROLLBACK');
            return res.status(404).json({ message: "그룹의 초대 코드를 찾을 수 없습니다." });
        }

        // 3. 각 전화번호를 group_members 테이블에 등록
        for (const contact of contacts) {
            const cleanPhone = contact.phone_number.replace(/[^0-9]/g, '');
            const inviteeName = contact.name;
            
            // 이미 그룹 멤버이거나 초대 중인지 확인 (중복 방지)
            const memberCheck = await client.query(
                'SELECT 1 FROM group_members WHERE group_id = $1 AND phone_number = $2',
                [groupId, cleanPhone]
            );

            if (memberCheck.rows.length === 0) {
                // PENDING 상태로 추가
                await client.query(
                    `INSERT INTO group_members 
                    (group_id, user_id, phone_number, member_status, joined_at, invite_code, invitee_name) 
                    VALUES ($1, $2, $3, 'PENDING', NOW(), $4, $5)`,
                    [groupId, sender_id, cleanPhone, inviteCode, inviteeName]
                );
            }
        }

        await client.query('COMMIT');
        res.status(200).json({ message: "초대 리스트에 등록되었습니다." });

    } catch (err) {
        await client.query('ROLLBACK');
        console.error('Error inviting members:', err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    } finally {
        client.release();
    }
});

module.exports = router;