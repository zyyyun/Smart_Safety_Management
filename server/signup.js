const express = require('express');
const router = express.Router();
const bcrypt = require('bcrypt');
const crypto = require('crypto');
const pool = require('./db');

// 13자리 랜덤 초대코드 생성 함수 (영문 대소문자, 숫자)
function generateInviteCode() {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    let result = '';
    for (let i = 0; i < 13; i++) {
        result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return result;
}

router.post('/signup', async (req, res) => {
    const { user_id, password, name, phone_num, email, user_role, group_id } = req.body;

    try {
        const hashedPassword = await bcrypt.hash(password, 10);

        // 관리자(manager)인 경우에만 초대코드 생성
        let inviteCode = null;
        if (user_role === 'manager') {
            inviteCode = generateInviteCode();
        }

        const newUser = await pool.query(
            `INSERT INTO users (user_id, password, name, phone_num, email, user_role, group_id, invite_code, created_at) 
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, NOW()) RETURNING *`,
            [user_id, hashedPassword, name, phone_num, email, user_role, group_id, inviteCode]
        );

        if (user_role === 'manager') {
            const groupResult = await pool.query(
                `INSERT INTO groups (manager_id, invite_code) VALUES ($1, $2) RETURNING group_id`,
                [user_id, inviteCode]
            );

            if (groupResult.rows.length > 0) {
                const newGroupId = groupResult.rows[0].group_id;
                await pool.query('UPDATE users SET group_id = $1 WHERE user_id = $2', [newGroupId, user_id]);
            }
        }

        res.status(201).json({
            userName: newUser.rows[0].name,
            invite_code: newUser.rows[0].invite_code // 생성된 초대코드 반환 (필요시)
        });

    } catch (err) {
        if (err.code === '23505') {
            return res.status(400).json({ message: "이미 존재하는 아이디입니다." });
        }
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;
