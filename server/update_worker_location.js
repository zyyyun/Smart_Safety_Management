const express = require('express');
const router = express.Router();
const pool = require('./db');
const { sendStatusAlarm } = require('./send_status_alarm'); // ✅ 알림 함수 가져오기

router.post('/update_worker_location', async (req, res) => {
    const { user_id, latitude, longitude, status } = req.body;

    if (!user_id || latitude === undefined || longitude === undefined) {
        return res.status(400).json({ message: "필수 정보가 누락되었습니다." });
    }

    try {
        // 1. 사용자의 그룹 ID 조회
        const userRes = await pool.query('SELECT group_id, user_role FROM users WHERE user_id = $1', [user_id]);
        if (userRes.rows.length === 0) {
            return res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }
        const { group_id: groupId, user_role: userRole } = userRes.rows[0];

        // ✅ 관리자(manager, general_manager)인 경우 위치 갱신 로직을 수행하지 않고 종료
        if (userRole !== 'worker') {
            return res.status(200).json({ message: "관리자는 위치 정보가 갱신되지 않습니다." });
        }

        let currentZone = null;
        let cameraId = null;

        // 2. PostGIS를 사용하여 현재 좌표가 포함된 구역 조회 (geofence_zones 테이블 사용)
        if (groupId) {
            const zoneRes = await pool.query(
                `SELECT zone_name 
                 FROM geofence_zones 
                 WHERE group_id = $1 
                   AND ST_Contains(boundary, ST_SetSRID(ST_MakePoint($2, $3), 4326))
                 LIMIT 1`,
                [groupId, longitude, latitude]
            );

            if (zoneRes.rows.length > 0) {
                currentZone = zoneRes.rows[0].zone_name;
            }
        }

        // 3. 위치 정보 갱신 (로그 쌓기 X -> 최신 상태 유지 O)
        const checkRes = await pool.query('SELECT log_id FROM location_logs WHERE user_id = $1 ORDER BY recorded_at DESC', [user_id]);

        if (checkRes.rows.length > 0) {
            // 기존 기록이 있다면 최신 기록 업데이트
            const logId = checkRes.rows[0].log_id;
            // ✅ status가 제공되면 업데이트, 아니면 기존 값 유지 (COALESCE)
            await pool.query(
                'UPDATE location_logs SET latitude = $1, longitude = $2, current_zone = $3, camera_id = $4, status = COALESCE($5, status), recorded_at = NOW() WHERE log_id = $6',
                [latitude, longitude, currentZone, cameraId, status, logId]
            );

            // 중복된 과거 데이터가 있다면 삭제 (데이터 정리)
            if (checkRes.rows.length > 1) {
                const idsToDelete = checkRes.rows.slice(1).map(r => r.log_id);
                await pool.query('DELETE FROM location_logs WHERE log_id = ANY($1::int[])', [idsToDelete]);
            }
        } else {
            // 기록이 없다면 신규 등록
            // ✅ 신규 등록 시 status가 없으면 '정상'으로 기본값 설정
            await pool.query(
                'INSERT INTO location_logs (user_id, latitude, longitude, current_zone, camera_id, status) VALUES ($1, $2, $3, $4, $5, $6)',
                [user_id, latitude, longitude, currentZone, cameraId, status || '정상']
            );
        }

        // ✅ 4. 상태가 정상이 아니면 관리자에게 알림 전송
        await sendStatusAlarm(pool, user_id, status, currentZone);

        res.status(200).json({ message: "위치 정보가 갱신되었습니다." });

    } catch (err) {
        console.error('Error updating worker location:', err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;