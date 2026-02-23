const admin = require("firebase-admin");

// Firebase 콘솔 -> 프로젝트 설정 -> 서비스 계정 -> 새 비공개 키 생성 후 다운로드한 파일
// 파일명을 serviceAccountKey.json으로 변경하여 server 폴더에 저장하세요.
const serviceAccount = require("./serviceAccountKey.json");

if (!admin.apps.length) {
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
    });
    console.log("✅ Firebase Admin SDK가 성공적으로 초기화되었습니다.");
}

module.exports = admin;