const http = require('http');

// 임의의 이벤트 데이터 생성
const eventData = JSON.stringify({
    device_type: 'fire_detector', // 기기 타입 (예: 화재경보기)
    device_id: 1,               // 기기 ID (임의 지정)
    group_id: 3,                  // 그룹 ID (임의 지정)
    event_type: 'ERROR'      // 이벤트 타입 (예: 화재 발생)
});

const options = {
    hostname: 'localhost',
    port: 3000,
    path: '/create_device_event_log',
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(eventData)
    }
};

console.log("이벤트 생성 요청 보내는 중...");

const req = http.request(options, (res) => {
    let data = '';

    res.on('data', (chunk) => {
        data += chunk;
    });

    res.on('end', () => {
        console.log(`상태 코드: ${res.statusCode}`);
        console.log('응답 본문:', JSON.parse(data));
    });
});

req.on('error', (e) => {
    console.error(`요청 중 오류 발생: ${e.message}`);
});

// 데이터 전송
req.write(eventData);
req.end();