const express = require('express');
const router = express.Router();
const pool = require('./db');

// 하버사인 공식을 이용한 거리 계산 (단위: 미터)
function getDistanceFromLatLonInM(lat1, lon1, lat2, lon2) {
    const R = 6371000; // 지구 반지름 (미터)
    const dLat = deg2rad(lat2 - lat1);
    const dLon = deg2rad(lon2 - lon1);
    const a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}

function deg2rad(deg) {
    return deg * (Math.PI / 180);
}

router.post('/update_worker_location', async (req, res) => {
    const { user_id, latitude, longitude } = req.body;

    if (!user_id || latitude === undefined || longitude === undefined) {
        return res.status(400).json({ message: "필수 정보가 누락되었습니다." });
    }

    try {
        // 1. 사용자의 그룹 ID 조회
        const userRes = await pool.query('SELECT group_id FROM users WHERE user_id = $1', [user_id]);
        if (userRes.rows.length === 0) {
            return res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }
        const groupId = userRes.rows[0].group_id;

        // 2. 해당 그룹의 카메라 목록 조회 (지오펜싱 판별용)
        // 좌표가 등록된 카메라만 조회
        const camerasRes = await pool.query(
            'SELECT camera_id, install_area, latitude, longitude FROM cameras WHERE group_id = $1 AND latitude IS NOT NULL AND longitude IS NOT NULL',
            [groupId]
        );

        let currentZone = null;
        let cameraId = null;

        // 3. 현재 위치가 카메라 반경 50m 이내인지 확인
        for (const cam of camerasRes.rows) {
            const dist = getDistanceFromLatLonInM(latitude, longitude, cam.latitude, cam.longitude);
            if (dist <= 50) { // 50m 이내
                currentZone = cam.install_area;
                cameraId = cam.camera_id;
                break; // 가장 가까운 하나만 찾으면 종료 (또는 로직에 따라 변경 가능)
            }
        }

        // 4. 위치 정보 갱신 (로그 쌓기 X -> 최신 상태 유지 O)
        const checkRes = await pool.query('SELECT log_id FROM location_logs WHERE user_id = $1 ORDER BY recorded_at DESC', [user_id]);

        if (checkRes.rows.length > 0) {
            // 기존 기록이 있다면 최신 기록 업데이트
            const logId = checkRes.rows[0].log_id;
            await pool.query(
                'UPDATE location_logs SET latitude = $1, longitude = $2, current_zone = $3, camera_id = $4, recorded_at = NOW() WHERE log_id = $5',
                [latitude, longitude, currentZone, cameraId, logId]
            );

            // 중복된 과거 데이터가 있다면 삭제 (데이터 정리)
            if (checkRes.rows.length > 1) {
                const idsToDelete = checkRes.rows.slice(1).map(r => r.log_id);
                await pool.query('DELETE FROM location_logs WHERE log_id = ANY($1::int[])', [idsToDelete]);
            }
        } else {
            // 기록이 없다면 신규 등록
            await pool.query(
                'INSERT INTO location_logs (user_id, latitude, longitude, current_zone, camera_id) VALUES ($1, $2, $3, $4, $5)',
                [user_id, latitude, longitude, currentZone, cameraId]
            );
        }

        res.status(200).json({ message: "위치 정보가 갱신되었습니다." });

    } catch (err) {
        console.error('Error updating worker location:', err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;