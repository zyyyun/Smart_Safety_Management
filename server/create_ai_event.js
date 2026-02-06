const express = require('express');
const router = express.Router();
const pool = require('./db');
const ffmpeg = require('fluent-ffmpeg');
const path = require('path');
const fs = require('fs');

// 업로드 디렉토리 설정
const uploadDir = path.join(__dirname, 'public', 'uploads');
if (!fs.existsSync(uploadDir)) {
    fs.mkdirSync(uploadDir, { recursive: true });
}

router.post('/create_ai_event', async (req, res) => {
    const { camera_id, accuracy, risk_level, event_name } = req.body;

    if (!camera_id || !event_name) {
        return res.status(400).json({ message: "camera_id와 event_name은 필수입니다." });
    }

    const client = await pool.connect();

    try {
        // Step 1: cameras 테이블에서 live_url_detail 조회
        const camRes = await client.query(
            'SELECT live_url_detail, device_name, install_area, installation_address, group_id FROM cameras WHERE camera_id = $1',
            [camera_id]
        );

        if (camRes.rows.length === 0) {
            return res.status(404).json({ message: "카메라를 찾을 수 없습니다." });
        }

        const camera = camRes.rows[0];
        const liveUrl = camera.live_url_detail;

        if (!liveUrl) {
            return res.status(400).json({ message: "해당 카메라에 live_url_detail이 설정되어 있지 않습니다." });
        }

        // Step 2: FFmpeg 스냅샷 촬영
        const filename = `detection_${camera_id}_${Date.now()}.jpg`;
        const savePath = path.join(uploadDir, filename);
        const dbImageUrl = `/uploads/${filename}`;

        // FFmpeg 실행 (Promise로 래핑하여 완료 대기)
        await new Promise((resolve, reject) => {
            ffmpeg(liveUrl)
                .inputOptions([
                    '-y',
                    '-analyzeduration', '1000000',
                    '-probesize', '1000000'
                ])
                .outputOptions([
                    '-frames:v', '1',
                    '-q:v', '2',
                    '-update', '1'
                ])
                .on('end', resolve)
                .on('error', reject)
                .save(savePath);
        });

        await client.query('BEGIN');

        // Step 3: camera_captures 테이블 생성
        const captureRes = await client.query(
            `INSERT INTO camera_captures (camera_id, image_url, captured_at, event_type)
             VALUES ($1, $2, NOW(), 'DETECTION')
             RETURNING capture_id`,
            [camera_id, dbImageUrl]
        );
        const captureId = captureRes.rows[0].capture_id;

        // Step 4: detection_events 테이블 생성
        // 4-1. event_types에서 type_id 조회
        let typeId = null;
        const typeRes = await client.query('SELECT id FROM event_types WHERE event_name = $1', [event_name]);
        
        if (typeRes.rows.length > 0) {
            typeId = typeRes.rows[0].id;
        } else {
            // 이벤트 타입이 없으면 새로 생성 (안전장치)
            const newTypeRes = await client.query('INSERT INTO event_types (event_name) VALUES ($1) RETURNING id', [event_name]);
            typeId = newTypeRes.rows[0].id;
        }

        // 4-2. detection_events 행 삽입
        // live_url 컬럼에 live_url_detail 값을 저장
        const insertEventQuery = `
            INSERT INTO detection_events 
            (camera_id, capture_id, type_id, accuracy, risk_level, detected_at, status, device_name, install_area, installation_address, live_url)
            VALUES ($1, $2, $3, $4, $5, NOW(), 'PENDING', $6, $7, $8, $9)
            RETURNING event_id
        `;

        const eventRes = await client.query(insertEventQuery, [
            camera_id, captureId, typeId, accuracy, risk_level,
            camera.device_name, camera.install_area, camera.installation_address, liveUrl
        ]);

        // ✅ [추가] 해당 그룹의 관리자(manager)들에게 알림 생성
        if (camera.group_id) {
            const managersRes = await client.query(
                "SELECT user_id FROM users WHERE group_id = $1 AND user_role = 'manager'",
                [camera.group_id]
            );

            if (managersRes.rows.length > 0) {
                const notiTitle = "AI 이벤트 감지";
                // 예: "C구역 1열 쓰러짐 감지"
                const notiContent = `${camera.install_area || ''} ${event_name} 감지`.trim();

                const notificationPromises = managersRes.rows.map(manager => {
                    return client.query(
                        'INSERT INTO notifications (user_id, title, content, created_at, is_read) VALUES ($1, $2, $3, NOW(), false)',
                        [manager.user_id, notiTitle, notiContent]
                    );
                });

                await Promise.all(notificationPromises);
            }
        }

        await client.query('COMMIT');

        res.status(200).json({
            message: "AI 이벤트가 생성되었습니다.",
            event_id: eventRes.rows[0].event_id,
            capture_id: captureId,
            image_url: dbImageUrl
        });

    } catch (err) {
        await client.query('ROLLBACK');
        console.error("AI 이벤트 생성 중 오류:", err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    } finally {
        client.release();
    }
});

module.exports = router;