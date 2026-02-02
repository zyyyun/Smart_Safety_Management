const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_event_types', async (req, res) => {
    try {
        // event_types 테이블에서 event_name 조회 (id 순 정렬)
        const result = await pool.query('SELECT event_name FROM event_types ORDER BY id ASC');
        
        const eventTypes = result.rows.map(row => row.event_name);
        res.status(200).json({ event_types: eventTypes });

    } catch (error) {
        console.error('이벤트 유형 조회 오류:', error);
        res.status(500).json({ message: '서버 오류가 발생했습니다.' });
    }
});

module.exports = router;