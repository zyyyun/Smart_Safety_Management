const cron = require('node-cron');
const pool = require('./db');
const ffmpeg = require('fluent-ffmpeg');
const path = require('path');
const fs = require('fs');

// 업로드 디렉토리 확인 및 생성
const uploadDir = path.join(__dirname, 'public', 'uploads');
if (!fs.existsSync(uploadDir)) {
    fs.mkdirSync(uploadDir, { recursive: true });
}

// 단일 카메라 스냅샷 캡처 및 저장 함수
const captureSnapshot = (camera) => {
    return new Promise((resolve, reject) => {
        const { camera_id, live_url_detail } = camera;
        const filename = `snapshot_${camera_id}_${Date.now()}.jpg`;
        const savePath = path.join(uploadDir, filename);
        
        // DB에 저장할 경로 (클라이언트 접근용)
        const dbImageUrl = `/uploads/${filename}`;

        ffmpeg(live_url_detail)
            .inputOptions([
                '-live_start_index', '-1',                // 출력 파일 덮어쓰기 허용
                '-analyzeduration', '1000000', 
                '-probesize', '1000000'
            ])
            .outputOptions([
                '-frames:v', '1',    // 딱 1프레임만 추출
                '-q:v', '2',         // 고화질 설정
                '-update', '1'       // 실시간 업데이트 모드
            ])
            .on('start', (commandLine) => {
                console.log(`[Snapshot] FFmpeg Start (Camera ${camera_id}): ${commandLine}`);
            })
            .on('end', async () => {
                try {
                    // DB에 캡처 정보 저장 (event_type은 'PERIODIC'으로 설정)
                    await pool.query(
                        `INSERT INTO camera_captures (camera_id, image_url, captured_at, event_type)
                         VALUES ($1, $2, NOW(), 'PERIODIC')`,
                        [camera_id, dbImageUrl]
                    );

                    // ✅ 최신 5개만 남기고 오래된 스냅샷 삭제 (파일 포함)
                    const oldSnapshots = await pool.query(
                        `SELECT capture_id, image_url FROM camera_captures
                         WHERE camera_id = $1
                         AND capture_id NOT IN (
                             SELECT capture_id
                             FROM camera_captures
                             WHERE camera_id = $1
                             ORDER BY captured_at DESC
                             LIMIT 5
                         )`,
                        [camera_id]
                    );

                    if (oldSnapshots.rows.length > 0) {
                        const idsToDelete = oldSnapshots.rows.map(row => row.capture_id);
                        
                        // 파일 삭제
                        oldSnapshots.rows.forEach(row => {
                            if (row.image_url) {
                                const filename = path.basename(row.image_url);
                                const filePath = path.join(uploadDir, filename);
                                fs.unlink(filePath, (err) => {
                                    if (err && err.code !== 'ENOENT') {
                                        console.error(`[Snapshot] Failed to delete file: ${filePath}`, err);
                                    }
                                });
                            }
                        });

                        // DB 삭제
                        await pool.query(
                            `DELETE FROM camera_captures WHERE capture_id = ANY($1::int[])`,
                            [idsToDelete]
                        );
                    }

                    console.log(`[Snapshot] Camera ${camera_id} captured.`);
                    resolve();
                } catch (dbErr) {
                    console.error(`[Snapshot] DB Error (Camera ${camera_id}):`, dbErr);
                    reject(dbErr);
                }
            })
            .on('error', (err) => {
                console.error(`[Snapshot] FFmpeg Error (Camera ${camera_id}):`, err);
                reject(err);
            })
            .save(savePath);
    });
};

// 스케줄러 시작 함수
const startCronJobs = () => {
    // 10분마다 실행 (*/10 * * * *)
    cron.schedule('*/10 * * * *', async () => {
        console.log('[Cron] Starting periodic snapshot capture...');
        try {
            // live_url_detail이 유효한 카메라 조회
            const result = await pool.query(
                "SELECT camera_id, live_url_detail FROM cameras WHERE live_url_detail IS NOT NULL AND live_url_detail != ''"
            );
            
            const cameras = result.rows;
            // 병렬 처리로 모든 카메라 스냅샷 시도
            await Promise.allSettled(cameras.map(camera => captureSnapshot(camera)));
        } catch (err) {
            console.error('[Cron] Error fetching cameras:', err);
        }
    });
    console.log('✅ Snapshot Scheduler initialized (Runs every 10 mins).');
};

module.exports = startCronJobs;