const express = require('express');
const router = express.Router();
const { SolapiMessageService } = require('solapi');
const redis = require('redis');

// 1. Redis 클라이언트 설정
const redisClient = redis.createClient({
    url: 'redis://redis:6379' // 도커 서비스 이름인 'redis' 사용
});

redisClient.on('error', (err) => console.log('Redis Client Error', err));

// 서버 시작 시 Redis 연결
(async () => {
    await redisClient.connect();
    console.log('Redis 연결 완료');
})();

// 2. CoolSMS (Solapi) 설정
const messageService = new SolapiMessageService("YOUR_API_KEY", "YOUR_API_SECRET");

// 3. 인증번호 발송 API
router.post('/send_verification', async (req, res) => {
    const { phone_num } = req.body;
    
    if (!phone_num) {
        return res.status(400).json({ message: "전화번호를 입력해주세요." });
    }

    // 6자리 난수 생성
    const code = Math.floor(100000 + Math.random() * 900000).toString();
    
    try {
        // Redis에 저장 (키: 전화번호, 값: 인증번호, EX: 만료시간 180초)
        await redisClient.set(phone_num, code, {
            EX: 180 
        });

        // 실제 SMS 발송
        await messageService.sendOne({
            to: phone_num,
            from: "YOUR_SENDER_NUMBER",
            text: `[Smart Safety] 인증번호 [${code}]를 입력해주세요.`
        });

        console.log(`[SMS 발송] ${phone_num} : ${code}`);
        res.status(200).json({ message: "인증번호가 발송되었습니다." });

    } catch (error) {
        console.error("발송 에러:", error);
        // 테스트용 로그
        console.log(`[테스트 모드] ${phone_num} -> ${code}`);
        res.status(200).json({ message: "인증번호가 발송되었습니다. (테스트)" });
    }
});

// 4. 인증번호 확인 API
router.post('/verify_code', async (req, res) => {
    const { phone_num, code } = req.body;

    try {
        // Redis에서 해당 번호의 코드 조회
        const savedCode = await redisClient.get(phone_num);

        if (savedCode && savedCode === code) {
            await redisClient.del(phone_num); // 인증 성공 시 즉시 삭제
            res.status(200).json({ message: "인증 성공", success: true });
        } else {
            res.status(400).json({ message: "인증번호가 일치하지 않거나 만료되었습니다.", success: false });
        }
    } catch (error) {
        console.error("Redis 조회 에러:", error);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;
