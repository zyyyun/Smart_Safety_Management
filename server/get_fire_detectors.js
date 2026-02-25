const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_fire_detectors', async (req, res) => {
    const { user_id } = req.query;

    if (!user_id) {
        return res.status(400).json({ message: "user_id가 필요합니다." });
    }

    try {
        // 1. 요청한 유저의 group_id 조회
        const userRes = await pool.query('SELECT group_id FROM users WHERE user_id = $1', [user_id]);
        if (userRes.rows.length === 0) {
            return res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }
        const groupId = userRes.rows[0].group_id;

        // 2. 해당 그룹의 화재경보기 조회
        const result = await pool.query(
            `SELECT detector_id, detector_name, is_active, status, 
             to_char(last_update, 'YYYY-MM-DD HH24:MI:SS') as last_update 
             FROM fire_detectors 
             WHERE group_id = $1 
             ORDER BY detector_id ASC`
        , [groupId]);
        res.status(200).json({ fire_detectors: result.rows });
    } catch (err) {
        console.error('Error fetching fire detectors:', err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;