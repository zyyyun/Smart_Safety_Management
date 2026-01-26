const express = require('express');
const app = express();
const port = 3000;

app.get('/', (req, res) => {
  res.send('스마트 안전 관리 서버 작동 중!');
});

app.listen(port, () => {
  console.log(`서버가 http://localhost:${port} 에서 실행 중입니다.`);
});