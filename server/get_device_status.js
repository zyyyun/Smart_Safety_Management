const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_device_status', async (req, res) => {
    const { user_id } = req.query;

    if (!user_id) {
        return res.status(400).json({ message: "user_id가 필요합니다." });
    }

    try {
        // 1. 요청한 유저의 group_id 조회
        const userResult = await pool.query('SELECT group_id FROM users WHERE user_id = $1', [user_id]);

        if (userResult.rows.length === 0) {
            return res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }

        const groupId = userResult.rows[0].group_id;

        if (!groupId) {
             return res.status(200).json({ device_status: [] });
        }

        // 2. 같은 group_id를 가진 유저들과 그들의 디바이스 정보 조회
        const query = `
            SELECT
                u.user_id,
                u.name,
                u.user_role,
                json_agg(
                    json_build_object(
                        'device_type', d.device_type,
                        'battery_level', d.battery_level,
                        'gps_status', d.gps_status
                    )
                ) FILTER (WHERE d.device_id IS NOT NULL) as devices
            FROM users u
            LEFT JOIN devices d ON u.user_id = d.user_id
            WHERE u.group_id = $1
            GROUP BY u.user_id, u.name, u.user_role
        `;

        const result = await pool.query(query, [groupId]);

        const deviceStatusList = result.rows.map(row => {
            const devices = row.devices || [];
            const helmet = devices.find(d => d.device_type && d.device_type.toLowerCase().includes('helmet'));
            const watch = devices.find(d => d.device_type && d.device_type.toLowerCase().includes('watch'));

            const isGpsConnected = (helmet && helmet.gps_status === '정상') && (watch && watch.gps_status === '정상');

            return {
                user_id: row.user_id,
                name: row.name,
                role: row.user_role === 'manager' ? '관리자' : '근로자',
                isGpsConnected: isGpsConnected,
                battery: helmet ? (helmet.battery_level || 0) : 0,
                watchBattery: watch ? (watch.battery_level || 0) : 0
            };
        });

        res.status(200).json({ device_status: deviceStatusList });

    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;
