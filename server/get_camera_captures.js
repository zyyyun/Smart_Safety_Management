const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_camera_captures', async (req, res) => {
    const { camera_id } = req.query;

    if (!camera_id) {
        return res.status(400).json({ message: "camera_id가 필요합니다." });
    }

    try {
        // 최신순으로 3개 조회
        const query = `
            SELECT capture_id, image_url, to_char(captured_at, 'YYYY-MM-DD HH24:MI:SS') as captured_at
            FROM camera_captures
            WHERE camera_id = $1
            AND event_type = 'PERIODIC'
            ORDER BY captured_at DESC
            LIMIT 3
        `;
        const result = await pool.query(query, [camera_id]);
        
        // 클라이언트 편의를 위해 상대 경로인 경우 전체 URL로 변환
        const captures = result.rows.map(row => ({
            ...row,
            image_url: row.image_url.startsWith('http') ? row.image_url : `${req.protocol}://${req.get('host')}${row.image_url}`
        }));

        res.status(200).json({ captures: captures });
    } catch (err) {
        console.error('Error fetching camera captures:', err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;