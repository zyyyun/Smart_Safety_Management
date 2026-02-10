const http = require('http');

// 서버 설정
const options = {
    hostname: 'localhost',
    port: 3000,
    path: '/update_watch_status',
    method: 'POST',
    headers: {
        'Content-Type': 'application/json'
    }
};

// 테스트할 데이터 (명령줄 인수로 받거나 기본값 사용)
// 실행 예: node test_update_watch.js user123 38.2
// 기본값: user_id='test_user', body_temp=38.5
const userId = process.argv[2] || 'asdf123';
const bodyTemp = parseFloat(process.argv[3]) || 38.5;

const postData = JSON.stringify({
    user_id: userId,
    body_temp: bodyTemp
});

console.log(`[TEST] Sending request to http://localhost:3000/update_watch_status`);
console.log(`[TEST] Payload: ${postData}`);

const req = http.request(options, (res) => {
    let data = '';

    res.on('data', (chunk) => {
        data += chunk;
    });

    res.on('end', () => {
        console.log(`[TEST] Status Code: ${res.statusCode}`);
        try {
            const jsonResponse = JSON.parse(data);
            console.log('[TEST] Response:', JSON.stringify(jsonResponse, null, 2));
        } catch (e) {
            console.log('[TEST] Response:', data);
        }
    });
});

req.on('error', (e) => {
    console.error(`[TEST] Problem with request: ${e.message}`);
});

// 데이터 전송
req.write(postData);
req.end();