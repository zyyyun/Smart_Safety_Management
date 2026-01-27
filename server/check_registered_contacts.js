const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/check_registered_contacts', async (req, res) => {
    const { phone_numbers } = req.body;

    if (!phone_numbers || !Array.isArray(phone_numbers) || phone_numbers.length === 0) {
        return res.status(200).json({ registered_phone_numbers: [] });
    }

    try {
        // 전달받은 전화번호 배열(phone_numbers) 중 users 테이블에 존재하는 번호 조회
        const query = 'SELECT phone_num FROM users WHERE phone_num = ANY($1)';
        const result = await pool.query(query, [phone_numbers]);

        const registeredPhoneNumbers = result.rows.map(row => row.phone_num);
        res.status(200).json({ registered_phone_numbers: registeredPhoneNumbers });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;