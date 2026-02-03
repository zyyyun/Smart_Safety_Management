const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/delete_cameras', async (req, res) => {
    const { camera_ids } = req.body; // [1, 2, 3] 형태의 배열

    if (!camera_ids || !Array.isArray(camera_ids) || camera_ids.length === 0) {
        return res.status(400).json({ message: "삭제할 카메라를 선택해주세요." });
    }

    try {
        // PostgreSQL의 ANY 연산자를 사용하여 한 번에 여러 행 삭제
        const query = 'DELETE FROM cameras WHERE camera_id = ANY($1::int[])';
        await pool.query(query, [camera_ids]);

        res.status(200).json({ message: "선택한 카메라가 삭제되었습니다." });
    } catch (err) {
        console.error('Error deleting cameras:', err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;