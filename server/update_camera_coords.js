const axios = require('axios');
const pool = require('./db');

// REST키
const KAKAO_API_KEY = '549ef0580861ccd75dc20bc5858e349f';

async function updateCameraCoordinates() {
    const client = await pool.connect();
    try {
        console.log("Starting camera coordinate update...");

        // 1. 주소는 있지만 좌표가 없는(또는 갱신할) 카메라 조회
        // installation_address 컬럼이 존재한다고 가정합니다.
        const res = await client.query(`
            SELECT camera_id, installation_address 
            FROM cameras 
            WHERE installation_address IS NOT NULL 
            AND installation_address != ''
        `);

        const cameras = res.rows;
        console.log(`Found ${cameras.length} cameras with addresses.`);

        for (const cam of cameras) {
            const address = cam.installation_address;
            
            try {
                // 2. 카카오 주소 검색 API 호출
                const apiRes = await axios.get('https://dapi.kakao.com/v2/local/search/address.json', {
                    headers: { Authorization: `KakaoAK ${KAKAO_API_KEY}` },
                    params: { query: address }
                });

                const documents = apiRes.data.documents;

                if (documents && documents.length > 0) {
                    // 카카오 API는 x가 경도(longitude), y가 위도(latitude)
                    const { x, y } = documents[0];
                    
                    // 3. DB 업데이트
                    await client.query(
                        `UPDATE cameras 
                         SET latitude = $1, longitude = $2 
                         WHERE camera_id = $3`,
                        [parseFloat(y), parseFloat(x), cam.camera_id]
                    );

                    console.log(`[Success] Camera ${cam.camera_id} (${address}) -> Lat: ${y}, Lon: ${x}`);
                } else {
                    console.log(`[Skipped] No coordinates found for Camera ${cam.camera_id} (${address})`);
                }

            } catch (apiErr) {
                console.error(`[Error] API call failed for Camera ${cam.camera_id}: ${apiErr.message}`);
            }
        }

        console.log("Update completed.");

    } catch (err) {
        console.error("Database connection error:", err);
    } finally {
        client.release();
        process.exit(); // 스크립트 종료
    }
}

// 스크립트 실행
updateCameraCoordinates();