const express = require('express');
const router = express.Router();
const pool = require('./db');

// 카메라 등록 API
router.post('/register_camera', async (req, res) => {
    const {
        device_name,
        device_code,
        install_area,
        group_id,
        live_url,
        live_url_detail,
        installation_address,
        latitude,
        longitude
    } = req.body;

    // 필수값 체크
    if (!device_name || !device_code) {
        return res.status(400).json({ message: "기기 이름(device_name)과 기기 코드(device_code)는 필수입니다." });
    }

    try {
        const query = `
            INSERT INTO cameras 
            (device_name, device_code, install_area, group_id, live_url, live_url_detail, installation_address, latitude, longitude, created_at)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW())
            RETURNING camera_id
        `;
        
        const values = [
            device_name, 
            device_code, 
            install_area, 
            group_id || null, 
            live_url || null, 
            live_url_detail || null, 
            installation_address || null, 
            latitude || null, 
            longitude || null
        ];

        const result = await pool.query(query, values);
        
        res.status(201).json({
            message: "카메라가 성공적으로 등록되었습니다.",
            camera_id: result.rows[0].camera_id
        });

    } catch (err) {
        console.error("카메라 등록 중 오류:", err);
        if (err.code === '23505') { // Unique violation (device_code)
            return res.status(409).json({ message: "이미 등록된 기기 코드(device_code)입니다." });
        }
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;