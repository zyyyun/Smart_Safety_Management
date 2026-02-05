const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/cancel_invite', async (req, res) => {
    const { sender_id, phone_number, phone_numbers } = req.body;

    // 단일 전화번호와 다중 전화번호 배열을 모두 처리하기 위해 리스트로 통합
    let targets = [];
    if (phone_numbers && Array.isArray(phone_numbers)) {
        targets = phone_numbers;
    } else if (phone_number) {
        targets = [phone_number];
    }

    if (!sender_id || targets.length === 0) {
        return res.status(400).json({ message: "필수 정보가 누락되었습니다." });
    }

    try {
        // 1. 관리자의 group_id 조회
        const userRes = await pool.query('SELECT group_id FROM users WHERE user_id = $1', [sender_id]);
        if (userRes.rows.length === 0 || !userRes.rows[0].group_id) {
            return res.status(404).json({ message: "그룹 정보를 찾을 수 없습니다." });
        }
        const groupId = userRes.rows[0].group_id;

        // 전화번호 정제 (숫자만 남김)
        const cleanPhones = targets.map(p => p.replace(/[^0-9]/g, ''));

        // 2. group_members에서 일괄 삭제 (PENDING 상태인 경우만, ANY 연산자 사용)
        const result = await pool.query(
            "DELETE FROM group_members WHERE group_id = $1 AND phone_number = ANY($2::text[]) AND member_status = 'PENDING'",
            [groupId, cleanPhones]
        );

        res.status(200).json({ message: `${result.rowCount}명의 초대가 취소되었습니다.` });

    } catch (err) {
        console.error('Error canceling invite:', err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;