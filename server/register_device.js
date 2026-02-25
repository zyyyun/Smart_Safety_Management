const express = require('express');
const router = express.Router();
const pool = require('./db');

// 기기(Watch, Helmet) 등록 API
router.post('/register_device', async (req, res) => {
    const { device_type, serial_number, user_id } = req.body;

    if (!device_type || !serial_number) {
        return res.status(400).json({ message: "기기 타입(device_type)과 시리얼 번호(serial_number)는 필수입니다." });
    }

    const type = device_type.toLowerCase();
    if (type !== 'watch' && type !== 'helmet') {
        return res.status(400).json({ message: "유효하지 않은 기기 타입입니다. (watch 또는 helmet)" });
    }

    const client = await pool.connect();

    try {
        await client.query('BEGIN'); // 트랜잭션 시작

        // 1. devices 테이블에 기본 정보 등록
        const deviceQuery = `
            INSERT INTO devices (device_type, serial_number, user_id, updated_at)
            VALUES ($1, $2, $3, NOW())
            RETURNING device_id
        `;
        const deviceRes = await client.query(deviceQuery, [type, serial_number, user_id || null]);
        const deviceId = deviceRes.rows[0].device_id;

        // 2. 기기 타입에 따라 상세 테이블에 등록
        if (type === 'watch') {
            // device_watches 테이블: 기본값으로 생성 (온도 36.5, 심박수 70)
            await client.query(
                `INSERT INTO device_watches (device_id, body_temp, heart_rate) VALUES ($1, 36.5, 70)`,
                [deviceId]
            );
        } else if (type === 'helmet') {
            // device_helmets 테이블: 기본값으로 생성 (미착용 횟수 0)
            await client.query(
                `INSERT INTO device_helmets (device_id, unworn_count) VALUES ($1, 0)`,
                [deviceId]
            );
        }

        await client.query('COMMIT'); // 트랜잭션 커밋

        res.status(201).json({
            message: `${type} 기기가 성공적으로 등록되었습니다.`,
            device_id: deviceId
        });

    } catch (err) {
        await client.query('ROLLBACK'); // 오류 발생 시 롤백
        console.error("기기 등록 중 오류:", err);
        if (err.code === '23505') { // Unique violation (serial_number)
            return res.status(409).json({ message: "이미 등록된 시리얼 번호입니다." });
        }
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    } finally {
        client.release();
    }
});

module.exports = router;