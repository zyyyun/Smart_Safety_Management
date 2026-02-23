const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/create_device_event_log', async (req, res) => {
    const { device_type, device_id, group_id, event_type } = req.body;

    // 필수 값 검증
    if (!device_type || !device_id || !group_id) {
        return res.status(400).json({ message: "device_type, device_id, group_id는 필수입니다." });
    }

    try {
        const result = await pool.query(
            `INSERT INTO device_event_logs (device_type, device_id, group_id, event_type, created_at)
             VALUES ($1, $2, $3, $4, NOW())
             RETURNING log_id, created_at`,
            [device_type, device_id, group_id, event_type]
        );

        // 같은 그룹의 관리자(manager) 조회
        const managersResult = await pool.query(
            "SELECT user_id FROM users WHERE group_id = $1 AND user_role = 'manager'",
            [group_id]
        );

        // 이벤트 타입에 따른 메시지 및 상태 변환
        let eventMsg = `${event_type} 발생`;
        let newStatus = null;

        if (event_type === 'ERROR') {
            eventMsg = '통신이상';
            newStatus = 'ERROR';
        } else if (event_type === 'OC_ALERT') {
            eventMsg = '과전류 주의';
            newStatus = 'ALERT';
        } else if (event_type === 'ARC_DETECT') {
            eventMsg = '아크 발생';
            newStatus = 'CRITICAL';
        }

        // 장비 이름 조회 및 타입 한글 변환
        let deviceName = '';
        let deviceTypeKr = device_type;

        if (device_type === 'fire_detector') {
            deviceTypeKr = '화재경보기';
            if (newStatus) {
                if (newStatus === 'ERROR') {
                    await pool.query('UPDATE fire_detectors SET status = $1, is_active = false, last_update = NOW() WHERE detector_id = $2', [newStatus, device_id]);
                } else {
                    await pool.query('UPDATE fire_detectors SET status = $1, last_update = NOW() WHERE detector_id = $2', [newStatus, device_id]);
                }
            }
            const devRes = await pool.query('SELECT detector_name FROM fire_detectors WHERE detector_id = $1', [device_id]);
            if (devRes.rows.length > 0) deviceName = devRes.rows[0].detector_name;
        } else if (device_type === 'arc_breaker') {
            deviceTypeKr = '아크차단기';
            if (newStatus) {
                await pool.query('UPDATE arc_breakers SET status = $1, status_msg = $2, last_event_at = NOW() WHERE breaker_id = $3', [newStatus, eventMsg, device_id]);
            }
            const devRes = await pool.query('SELECT breaker_name FROM arc_breakers WHERE breaker_id = $1', [device_id]);
            if (devRes.rows.length > 0) deviceName = devRes.rows[0].breaker_name;
        }

        // 장비 이름을 찾지 못한 경우 ID로 대체 (예외 처리)
        if (!deviceName) {
            deviceName = `${deviceTypeKr} (ID: ${device_id})`;
        }

        // 관리자들에게 알림 전송
        if (managersResult.rows.length > 0) {
            const title = `[장비 알림] ${deviceTypeKr} 이벤트`;
            const content = `${deviceName} ${eventMsg}`;

            const notificationPromises = managersResult.rows.map(manager => {
                return pool.query(
                    'INSERT INTO notifications (user_id, title, content) VALUES ($1, $2, $3)',
                    [manager.user_id, title, content]
                );
            });

            await Promise.all(notificationPromises);
            console.log(`[DeviceEvent] Sent notifications to ${managersResult.rows.length} managers.`);
        }

        res.status(200).json({
            message: "이벤트 로그가 생성되었습니다.",
            log: result.rows[0]
        });
    } catch (err) {
        console.error('Error creating device event log:', err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;