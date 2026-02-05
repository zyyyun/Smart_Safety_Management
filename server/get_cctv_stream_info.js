const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_cctv_stream_info', async (req, res) => {
    const { camera_id } = req.query;

    if (!camera_id) {
        return res.status(400).json({ message: "camera_id가 필요합니다." });
    }

    try {
        const query = `
            SELECT 
                camera_id, 
                live_url, 
                live_url_detail,
                install_area,
                installation_address
            FROM cameras 
            WHERE camera_id = $1
        `;

        const result = await pool.query(query, [camera_id]);

        if (result.rows.length === 0) {
            return res.status(404).json({ message: "카메라를 찾을 수 없습니다." });
        }

        res.status(200).json(result.rows[0]);

    } catch (err) {
        console.error('Error fetching CCTV stream info:', err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;