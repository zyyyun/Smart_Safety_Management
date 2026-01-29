const express = require('express');
const router = express.Router();
const { SolapiMessageService } = require('solapi');

// 1. Solapi 설정
// 실제 발송을 원하시면 YOUR_ 부분을 본인의 API 키로 교체하세요.
const messageService = new SolapiMessageService("YOUR_API_KEY", "YOUR_API_SECRET");

// 2. 인증번호 임시 저장소 (메모리 사용)
const tempStorage = {};

// [테스트용] 접속 확인
router.get('/sms-test', (req, res) => {
    res.send('✅ SMS 라우터가 정상적으로 로드되었습니다!');
});

// 3. 인증번호 발송 API
router.post('/send_verification', async (req, res) => {
    const { phone_num } = req.body;
    console.log(`\n[인증요청] 수신번호: ${phone_num}`);

    if (!phone_num) {
        return res.status(400).json({ message: "전화번호를 입력해주세요." });
    }

    // 6자리 인증번호 생성
    const code = Math.floor(100000 + Math.random() * 900000).toString();
    
    // 만료시간 설정 (3분)
    const expiry = Date.now() + 3 * 60 * 1000;
    
    // 메모리에 객체 형태로 저장
    tempStorage[phone_num] = { code, expiry };
    console.log(`[생성된 번호] ${phone_num} : ${code} (만료: ${new Date(expiry).toLocaleTimeString()})`);

    try {
        // 실제 발송 시도
        await messageService.sendOne({
            to: phone_num,
            from: "YOUR_SENDER_NUMBER", // 발신번호 등록 필요
            text: `[Smart Safety] 인증번호 [${code}]를 입력해주세요.`
        });

        res.status(200).json({ message: "인증번호가 발송되었습니다." });

    } catch (error) {
        // API 키가 없거나 실패해도 로그를 보고 테스트할 수 있도록 함
        console.log("⚠️ 실제 문자 발송 실패 (테스트 모드 유지):", error.message);
        res.status(200).json({ 
            message: "인증번호가 발송되었습니다. (테스트 모드)",
            debug_code: code 
        });
    }
});

// 4. 인증번호 확인 API
router.post('/verify_code', async (req, res) => {
    const { phone_num, code } = req.body;
    console.log(`[인증확인 시도] 번호: ${phone_num}, 입력코드: ${code}`);

    const data = tempStorage[phone_num];

    if (data && data.code === code) {
        // 시간 만료 체크
        if (Date.now() > data.expiry) {
            delete tempStorage[phone_num];
            return res.status(400).json({ success: false, message: "인증 시간이 만료되었습니다." });
        }
        
        delete tempStorage[phone_num]; // 성공 시 1회성 사용
        res.status(200).json({ success: true, message: "인증 성공" });
    } else {
        res.status(400).json({ success: false, message: "인증번호가 일치하지 않습니다." });
    }
});

module.exports = router;
