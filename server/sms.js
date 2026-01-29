const express = require('express');
const router = express.Router();
const { SolapiMessageService } = require('solapi');
const redis = require('redis');

// 1. Redis 클라이언트 설정
// 로컬(터미널)에서 서버를 돌릴 때는 localhost:6379가 기본입니다.
const redisClient = redis.createClient({
    url: 'redis://127.0.0.1:6379' // localhost 대신 127.0.0.1로 더 확실하게 지정
});

redisClient.on('error', (err) => {
    console.log('Redis 연결 대기 중 또는 오류:', err.message);
});

// 서버 시작 시 Redis 연결
(async () => {
    try {
        await redisClient.connect();
        console.log('✅ Redis 서버에 정상적으로 연결되었습니다.');
    } catch (err) {
        console.error('❌ Redis 연결 실패! 도커에서 redis가 실행 중인지 확인하세요.');
        console.error('명령어: docker-compose up -d');
    }
})();

// 2. CoolSMS (Solapi) 설정
const messageService = new SolapiMessageService("YOUR_API_KEY", "YOUR_API_SECRET");

// 3. 인증번호 발송 API
router.post('/send_verification', async (req, res) => {
    const { phone_num } = req.body;
    
    if (!phone_num) {
        return res.status(400).json({ message: "전화번호를 입력해주세요." });
    }

    const code = Math.floor(100000 + Math.random() * 900000).toString();
    
    try {
        if (redisClient.isOpen) {
            await redisClient.set(phone_num, code, { EX: 180 });
            console.log(`[Redis 저장 완료] ${phone_num} : ${code}`);
        }

        await messageService.sendOne({
            to: phone_num,
            from: "YOUR_SENDER_NUMBER",
            text: `[Smart Safety] 인증번호 [${code}]를 입력해주세요.`
        });

        res.status(200).json({ message: "인증번호가 발송되었습니다." });

    } catch (error) {
        // 실제 발송 실패 시에도 개발 중에는 로그를 보고 테스트할 수 있도록 함
        console.log(`[인증번호 로그(테스트용)] 번호: ${phone_num}, 번호: ${code}`);
        res.status(200).json({ message: "인증번호가 발송되었습니다. (테스트 모드)" });
    }
});

// 4. 인증번호 확인 API
router.post('/verify_code', async (req, res) => {
    const { phone_num, code } = req.body;

    try {
        if (!redisClient.isOpen) {
            return res.status(500).json({ message: "서버 Redis 연결 오류" });
        }

        const savedCode = await redisClient.get(phone_num);

        if (savedCode && savedCode === code) {
            await redisClient.del(phone_num);
            res.status(200).json({ message: "인증 성공", success: true });
        } else {
            res.status(400).json({ message: "인증번호가 올바르지 않거나 만료되었습니다.", success: false });
        }
    } catch (error) {
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;
