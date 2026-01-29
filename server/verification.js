const express = require('express');
const router = express.Router();

// 인증번호를 임시 저장할 객체 (실무에서는 Redis 권장)
const verificationCodes = {};

// 1. 인증번호 전송 API
router.post('/send_verification_code', async (req, res) => {
    const { phone_num, app_hash } = req.body; // 앱에서 보낸 app_hash를 받음
    
    // 6자리 난수 생성
    const code = Math.floor(100000 + Math.random() * 900000).toString();
    
    // 인증번호 저장 (3분 유효)
    verificationCodes[phone_num] = {
        code: code,
        expires: Date.now() + 180000 // 3분 후 만료
    };

    console.log(`[SMS 발송 요청] 번호: ${phone_num}, 인증코드: ${code}, 앱해시: ${app_hash}`);

    // SMS Retriever를 위한 메시지 구성
    // 앱에서 보내준 app_hash를 메시지 끝에 붙임으로써 자동화 완료
    const message = `<#> [Smart Safety] 인증번호는 [${code}]입니다.\n${app_hash || ''}`;
    
    console.log(`실제 발송될 메시지 내용:\n${message}`);

    // TODO: 실제 SMS 발송 API 연동 시 위 message 변수를 사용하면 됩니다.
    
    res.status(200).json({ message: "인증번호가 발송되었습니다." });
});

// 2. 인증번호 확인 API
router.post('/check_verification_code', (req, res) => {
    const { phone_num, verification_code } = req.body;
    
    const record = verificationCodes[phone_num];

    if (!record) {
        return res.status(400).json({ message: "인증 요청 이력이 없습니다.", isVerified: false });
    }

    if (Date.now() > record.expires) {
        delete verificationCodes[phone_num];
        return res.status(400).json({ message: "인증 시간이 만료되었습니다.", isVerified: false });
    }

    if (record.code === verification_code) {
        delete verificationCodes[phone_num]; // 인증 성공 후 삭제
        return res.status(200).json({ message: "인증 성공", isVerified: true });
    } else {
        return res.status(400).json({ message: "인증번호가 일치하지 않습니다.", isVerified: false });
    }
});

module.exports = router;
