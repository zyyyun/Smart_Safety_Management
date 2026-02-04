const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/check_invite_availability', async (req, res) => {
    const { user_id, phone_numbers } = req.body;

    if (!user_id || !phone_numbers || !Array.isArray(phone_numbers) || phone_numbers.length === 0) {
        return res.status(400).json({ message: "필수 정보가 누락되었습니다." });
    }

    // 전화번호 하이픈 제거 (DB와 비교를 위해 정규화)
    const cleanPhoneNumbers = phone_numbers.map(num => num.replace(/[^0-9]/g, ''));

    try {
        // 1. 관리자의 group_id 조회
        const groupResult = await pool.query('SELECT group_id FROM users WHERE user_id = $1', [user_id]);
        if (groupResult.rows.length === 0 || !groupResult.rows[0].group_id) {
            return res.status(404).json({ message: "그룹 정보를 찾을 수 없습니다." });
        }
        const groupId = groupResult.rows[0].group_id;

        // 2. 초대 가능한 연락처 필터링 쿼리
        // - users 테이블에 존재해야 함 (앱 가입자)
        // - users 테이블에서 이미 내 그룹(group_id)에 속한 사람은 제외
        // - group_members 테이블에서 이미 초대중(PENDING)이거나 가입됨(ACTIVE)인 사람은 제외
        const query = `
            WITH input_phones AS (
                SELECT UNNEST($1::text[]) AS phone_num
            ),
            existing_group_users AS (
                SELECT REGEXP_REPLACE(phone_num, '[^0-9]', '', 'g') as phone_num
                FROM users
                WHERE group_id = $2 AND phone_num IS NOT NULL
            ),
            existing_invites AS (
                SELECT REGEXP_REPLACE(phone_number, '[^0-9]', '', 'g') as phone_num
                FROM group_members
                WHERE group_id = $2 
                AND member_status IN ('PENDING', 'ACTIVE')
                AND phone_number IS NOT NULL
            )
            SELECT ip.phone_num
            FROM input_phones ip
            WHERE ip.phone_num NOT IN (SELECT phone_num FROM existing_group_users)
            AND ip.phone_num NOT IN (SELECT phone_num FROM existing_invites)
        `;

        const result = await pool.query(query, [cleanPhoneNumbers, groupId]);
        
        // 필터링된(초대 가능한) 전화번호 목록 반환
        const availablePhoneNumbers = result.rows.map(row => row.phone_num);
        res.status(200).json({ available_phone_numbers: availablePhoneNumbers });

    } catch (err) {
        console.error('Error checking invite availability:', err);
        res.status(500).json({ message: "서버 오류가 발생했습니다." });
    }
});

module.exports = router;