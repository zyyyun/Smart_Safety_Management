const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/register_workplace_location', async (req, res) => {
    const { user_id, address, road_address, latitude, longitude } = req.body;

    try {
        // workplace 테이블에 주소 정보 저장 (UPSERT 방식)
        // 해당 사용자의 workplace 정보가 있는지 확인
        const workplaceResult = await pool.query('SELECT * FROM workplace WHERE admin_id = $1', [user_id]);

        if (workplaceResult.rows.length > 0) {
            // 이미 존재하면 업데이트
            // 테이블 스키마에 맞춰 zipcode, created_at 제거
            await pool.query(
                'UPDATE workplace SET address = $1, road_address = $2, latitude = $3, longitude = $4 WHERE admin_id = $5',
                [address, road_address, latitude, longitude, user_id]
            );
        } else {
            // 없으면 새로 생성
            // place_name이 NOT NULL이므로 기본값('내 현장') 추가
            await pool.query(
                'INSERT INTO workplace (admin_id, place_name, address, road_address, latitude, longitude) VALUES ($1, $2, $3, $4, $5, $6)',
                [user_id, '내 현장', address, road_address, latitude, longitude]
            );
        }

        res.status(200).json({ message: "위치 등록 성공" });

    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;