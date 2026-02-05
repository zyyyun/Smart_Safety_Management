const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_cctv_list', async (req, res) => {
    // ✅ [디버깅] 클라이언트가 보낸 실제 파라미터 전체 확인
    console.log('[get_cctv_list] req.query 전체:', JSON.stringify(req.query, null, 2));

    const { user_id, area } = req.query;
    // ✅ Retrofit에서 @Query("event")로 보냈을 경우를 대비해 events 또는 event 모두 확인
    // ✅ 배열 파라미터가 'events[]' 키로 들어오는 경우도 처리
    // ✅ [수정] 클라이언트가 'event_names'로 보내는 경우도 처리 추가
    const events = req.query.events || req.query.event || req.query['events[]'] || req.query.event_names;
    console.log(`[get_cctv_list] 추출된 events 변수 값:`, events);

    if (!user_id) {
        return res.status(400).json({ message: "user_id가 필요합니다." });
    }

    try {
        // 1. 사용자의 group_id 조회
        const userResult = await pool.query('SELECT group_id FROM users WHERE user_id = $1', [user_id]);

        if (userResult.rows.length === 0) {
            return res.status(404).json({ message: "사용자를 찾을 수 없습니다." });
        }

        const groupId = userResult.rows[0].group_id;

        // 2. CCTV 리스트 조회
        // ✅ 동적 쿼리 생성을 위해 let 사용
        let query = `
            SELECT 
                c.camera_id, 
                c.device_name, 
                c.install_area, 
                c.image_res_name, 
                c.environment_type, 
                c.installation_address,
                c.live_url,
                c.live_url_detail,
                (
                    SELECT COALESCE(json_agg(et.event_name), '[]')
                    FROM camera_events ce
                    JOIN event_types et ON ce.event_type_id = et.id
                    WHERE ce.camera_id = c.camera_id
                ) as events
            FROM cameras c
            WHERE c.group_id = $1
        `;

        const params = [groupId];
        let paramIndex = 2;

        // ✅ 구역 필터링 (area 파라미터가 있을 경우)
        if (area && area !== 'null') {
            // ✅ [수정] '=' (정확히 일치) 대신 'LIKE' (포함)으로 변경
            query += ` AND c.install_area LIKE $${paramIndex}`;
            params.push(`%${area}%`);
            paramIndex++;
        }

        // ✅ 이벤트 필터링 (events 파라미터가 있을 경우)
        if (events) {
            // events가 하나면 문자열, 여러개면 배열로 들어옴 -> 배열로 통일
            // ✅ [수정] AND 연산을 위해 중복 제거 (개수 비교 시 정확성 보장)
            const eventsArray = [...new Set(Array.isArray(events) ? events : [events])];
            
            console.log(`[get_cctv_list] 이벤트 필터 적용: ${JSON.stringify(eventsArray)}`);
            
            // ✅ [수정] OR(EXISTS) -> AND(COUNT 비교) 로 변경
            // 선택된 이벤트들을 모두 가지고 있는 카메라만 조회
            query += ` AND (
                SELECT COUNT(DISTINCT et.event_name)
                FROM camera_events ce 
                JOIN event_types et ON ce.event_type_id = et.id 
                WHERE ce.camera_id = c.camera_id 
                AND et.event_name = ANY($${paramIndex}::text[])
            ) = $${paramIndex + 1}`;
            params.push(eventsArray);
            params.push(eventsArray.length);
            paramIndex += 2;
        } else {
            console.log(`[get_cctv_list] 이벤트 필터 미적용 (events 변수가 없음)`);
        }

        query += ` ORDER BY c.camera_id ASC`;

        // ✅ [디버깅] 최종 실행될 쿼리와 파라미터 확인
        console.log('[get_cctv_list] 최종 실행 Query:', query);
        console.log('[get_cctv_list] 최종 Params:', params);

        const result = await pool.query(query, params);
        res.status(200).json({ cctv_list: result.rows });

    } catch (err) {
        console.error('Error fetching CCTV list:', err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;