const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_pending_invites', async (req, res) => {
    const { user_id } = req.query;

    if (!user_id) {
        return res.status(400).json({ message: "user_id가 필요합니다." });
    }

    try {
        // 1. 관리자의 group_id 조회
        const userRes = await pool.query('SELECT group_id FROM users WHERE user_id = $1', [user_id]);
        if (userRes.rows.length === 0 || !userRes.rows[0].group_id) {
            return res.status(404).json({ message: "그룹 정보를 찾을 수 없습니다." });
        }
        const groupId = userRes.rows[0].group_id;

        // 2. PENDING 상태인 멤버 조회 (users 테이블과 조인하여 이름/역할 확인)
        const query = `
            SELECT 
                gm.phone_number, 
                COALESCE(u.name, gm.invitee_name, '이름 없음') as name,
                u.user_role
            FROM group_members gm
            LEFT JOIN users u ON gm.phone_number = REGEXP_REPLACE(u.phone_num, '[^0-9]', '', 'g')
            WHERE gm.group_id = $1 AND gm.member_status = 'PENDING'
        `;

        const result = await pool.query(query, [groupId]);
        res.status(200).json({ pending_invites: result.rows });

    } catch (err) {
        console.error('Error fetching pending invites:', err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;