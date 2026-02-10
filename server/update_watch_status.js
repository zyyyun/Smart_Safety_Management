const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/update_watch_status', async (req, res) => {
    const { user_id, body_temp } = req.body;

    if (!user_id || body_temp === undefined) {
        return res.status(400).json({ message: "user_id와 body_temp가 필요합니다." });
    }

    const client = await pool.connect();
    try {
        await client.query('BEGIN');

        // 1. 사용자의 Watch 기기 찾기 (device_type에 'watch'가 포함된 기기 검색)
        const deviceRes = await client.query(
            "SELECT device_id FROM devices WHERE user_id = $1 AND device_type ILIKE '%watch%'",
            [user_id]
        );

        if (deviceRes.rows.length === 0) {
            await client.query('ROLLBACK');
            return res.status(404).json({ message: "해당 사용자의 워치 기기를 찾을 수 없습니다." });
        }

        const deviceId = deviceRes.rows[0].device_id;

        // 2. device_watches 테이블의 body_temp 업데이트 (UPSERT 처리)
        // 기기가 존재하면 업데이트, 없으면 삽입
        await client.query(
            `INSERT INTO device_watches (device_id, body_temp)
             VALUES ($1, $2)
             ON CONFLICT (device_id) 
             DO UPDATE SET body_temp = EXCLUDED.body_temp`,
            [deviceId, body_temp]
        );

        // 3. 체온에 따른 상태 결정 (38도 이상이면 '고열', 아니면 '정상')
        let newStatus = '정상';
        if (parseFloat(body_temp) >= 38.0) {
            newStatus = '고열';
        }

        // 4. location_logs 테이블의 최신 로그 상태 업데이트
        // 가장 최근 로그 하나만 업데이트하여 지도 마커 상태 변경
        const logRes = await client.query(
            "SELECT log_id FROM location_logs WHERE user_id = $1 ORDER BY recorded_at DESC LIMIT 1",
            [user_id]
        );

        if (logRes.rows.length > 0) {
            const logId = logRes.rows[0].log_id;
            // 기존 상태가 '추락' 등 더 심각한 상태가 아닐 때만 업데이트하거나, 
            // 여기서는 체온 모니터링이 우선이라 가정하고 업데이트합니다.
            await client.query(
                "UPDATE location_logs SET status = $1 WHERE log_id = $2",
                [newStatus, logId]
            );
        }

        await client.query('COMMIT');
        res.status(200).json({ message: "체온 및 상태가 업데이트되었습니다.", status: newStatus });

    } catch (err) {
        await client.query('ROLLBACK');
        console.error('Error updating watch status:', err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    } finally {
        client.release();
    }
});

module.exports = router;