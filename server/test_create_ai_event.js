// ✅ AI 이벤트 생성 API 테스트 스크립트
// 실행 방법: 터미널에서 `node test_create_ai_event.js` 입력

const http = require('http');

// 명령줄 인수로 user_id 받기 (예: node test_create_ai_event.js user1)
const targetUserId = process.argv[2] || null;

// 👇 테스트할 데이터 (DB에 존재하는 실제 camera_id로 변경 필요)
const postData = JSON.stringify({
    camera_id: 1,         // ⚠️ 실제 존재하는 카메라 ID 입력
    accuracy: 88.5,
    risk_level: 'danger', // danger, warning, caution
    event_name: '쓰러짐',   // fire, fall, intrusion 등
    user_id: targetUserId // ✅ 추가: 상태를 변경할 작업자 ID
});

const options = {
    hostname: 'localhost',
    port: 3000,
    path: '/create_ai_event',
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(postData)
    }
};

console.log(`[Test] 요청 보내는 중... (Target: http://localhost:3000/create_ai_event)`);
console.log(`[Test] 데이터: ${postData}`);

const req = http.request(options, (res) => {
    let data = '';

    res.on('data', (chunk) => {
        data += chunk;
    });

    res.on('end', () => {
        console.log(`\n[Response] 상태 코드: ${res.statusCode}`);
        console.log(`[Response] 응답 본문: ${data}`);
    });
});

req.on('error', (e) => {
    console.error(`[Error] 요청 실패: ${e.message}`);
});

// 요청 전송
req.write(postData);
req.end();