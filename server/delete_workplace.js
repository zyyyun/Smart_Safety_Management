const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/delete_workplace', async (req, res) => {
    const { place_name, admin_id } = req.body;

    try {
        const result = await pool.query(
            'DELETE FROM workplace WHERE place_name = $1 AND admin_id = $2',
            [place_name, admin_id]
        );

        if (result.rowCount > 0) {
            res.status(200).json({ message: "현장 삭제 성공" });
        } else {
            // 삭제 실패 시 원인 파악: 현장 이름은 존재하지만 admin_id가 다른지 확인
            const check = await pool.query('SELECT admin_id FROM workplace WHERE place_name = $1', [place_name]);
            
            if (check.rows.length > 0) {
                // 현장은 존재하지만 요청한 admin_id와 다를 경우 (권한 없음)
                return res.status(403).json({ message: "삭제 권한이 없습니다. (다른 사용자가 생성한 현장입니다)" });
            }
            
            res.status(404).json({ message: "현장을 찾을 수 없습니다." });
        }
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;