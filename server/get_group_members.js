const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_group_members', async (req, res) => {
    const { user_id } = req.query;

    if (!user_id) {
        return res.status(400).json({ message: 'user_id가 필요합니다.' });
    }

    try {
        // 1. 요청한 사용자의 group_id 조회
        const userResult = await pool.query(
            'SELECT group_id FROM users WHERE user_id = $1',
            [user_id]
        );

        if (userResult.rows.length === 0) {
            return res.status(404).json({ message: '사용자를 찾을 수 없습니다.' });
        }

        const groupId = userResult.rows[0].group_id;

        // 그룹이 없는 경우 빈 목록 반환
        if (!groupId) {
            return res.status(200).json({ members: [] });
        }

        // 2. 같은 group_id를 가진 사용자들의 이름 조회
        const membersResult = await pool.query('SELECT name FROM users WHERE group_id = $1', [groupId]);
        const members = membersResult.rows.map(row => row.name);
        res.status(200).json({ members });

    } catch (error) {
        console.error('그룹 멤버 조회 오류:', error);
        res.status(500).json({ message: '서버 오류가 발생했습니다.' });
    }
});

module.exports = router;