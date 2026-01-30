const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_daily_checks', async (req, res) => {
    const { user_id, year, month } = req.query;

    if (!user_id || !year || !month) {
        return res.status(400).json({ message: "필수 파라미터가 누락되었습니다." });
    }

    try {
        // 1. 요청한 유저의 group_id 조회
        const userResult = await pool.query('SELECT group_id FROM users WHERE user_id = $1', [user_id]);

        if (userResult.rows.length === 0) {
            return res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }

        const groupId = userResult.rows[0].group_id;

        // 2. 해당 그룹에 속한 작성자가 쓴 점검 리스트 조회 (년/월 필터링)
        // daily_safety_check 테이블의 writer_id가 users 테이블의 user_id와 일치하고, 그 user의 group_id가 일치하는 경우
        const query = `
            SELECT 
                d.check_id, 
                d.writer_id, 
                d.worker_id, 
                d.location, 
                d.hazard, 
                d.countermeasure, 
                d.status, 
                to_char(d.check_date, 'YYYY-MM-DD') as check_date,
                to_char(d.created_at, 'YYYY-MM-DD HH24:MI:SS') as created_at,
                (SELECT COALESCE(json_agg(image_url), '[]') FROM check_images WHERE check_id = d.check_id) as images
            FROM daily_safety_check d
            JOIN users u ON d.writer_id = u.user_id
            WHERE u.group_id = $1
            AND to_char(d.created_at, 'YYYY') = $2::text
            AND to_char(d.created_at, 'FMMM') = $3::text
            ORDER BY d.created_at DESC
        `;

        const result = await pool.query(query, [groupId, year, month]);
        res.status(200).json({ checks: result.rows });
    } catch (err) {
        console.error('Error fetching daily checks:', err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;