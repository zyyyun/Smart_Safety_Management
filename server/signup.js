const express = require('express');
const router = express.Router();
const bcrypt = require('bcrypt');
const pool = require('./db'); // 이전에 설정한 PostgreSQL 연결 파일

// 최종 회원가입 요청 ( 완료버튼을 통한 요청.)
router.post('/signup', async (req, res) => {
    const { user_id, password, name, phone_num, email, user_role, group_id } = req.body;

    try {
        // 1. 비밀번호 암호화
        const hashedPassword = await bcrypt.hash(password, 10);

        // 2. DB 저장 (사용자님이 만든 테이블 구조 기준)
        const newUser = await pool.query(
            `INSERT INTO users (user_id, password, name, phone_num, email, user_role, group_id, created_at) 
             VALUES ($1, $2, $3, $4, $5, $6, $7, NOW()) RETURNING *`,
            [user_id, hashedPassword, name, phone_num, email, user_role, group_id]
        );

        // 3. 가입 완료 메시지와 이름 반환 (4단계 화면용)
        res.status(201).json({
            userName: newUser.rows[0].name
        });

    } catch (err) {
        if (err.code === '23505') { // PostgreSQL 중복 키 에러 코드
            return res.status(400).json({ message: "이미 존재하는 아이디입니다." });
        }
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;