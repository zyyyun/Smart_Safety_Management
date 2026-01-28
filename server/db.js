const { Pool } = require('pg');

// PostgreSQL 데이터베이스 연결을 위한 Pool 생성
// Pool은 여러 클라이언트의 동시 접속을 효율적으로 관리합니다.
const pool = new Pool({
    user: 'safety_user',      // PostgreSQL 사용자 이름으로 변경하세요.
    host: 'localhost',              // 데이터베이스 서버 호스트 (일반적으로 localhost)
    database: 'safety_management', // 생성한 데이터베이스 이름으로 변경하세요.
    password: 'password123',      // 데이터베이스 비밀번호로 변경하세요.
    port: 5432,                     // PostgreSQL 기본 포트
    max: 20,                        // 최대 연결 수 제한 (기본값 10, 필요에 따라 조절)
    idleTimeoutMillis: 30000,       // 연결이 유휴 상태일 때 종료되는 시간 (30초)
    connectionTimeoutMillis: 2000,  // 연결 시도 타임아웃 (2초)
});

// 연결 에러 로그 출력 (서버가 죽지 않도록 방지)
pool.on('error', (err, client) => {
    console.error('Unexpected error on idle client', err);
    process.exit(-1);
});

// 생성된 pool 객체를 다른 파일(signup.js 등)에서 사용할 수 있도록 내보냅니다.
module.exports = pool;