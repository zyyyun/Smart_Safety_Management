# reference_media — 파일럿 테스트용 레퍼런스 영상

실제 Drift X3 RTSP 카메라가 설치되기 전(2026-06 공사 착수)까지
ai_agent 테스트용 "가상 카메라 피드"로 사용하는 레퍼런스 영상 보관소.

## 구조

```
reference_media/
├── fire/        # 화재 장면
├── helmet/      # 안전모 미착용 장면
├── fall/        # 쓰러짐·낙상 장면
├── person/      # 일반 작업자 이동 장면
└── forklift/    # 지게차 이동·충돌 장면
```

## 파일 규칙

- **포맷**: `.mp4` 권장 (H.264). `.gif`, `.mov`도 FFmpeg가 받아줌
- **내용**: 실제 공사현장/산업현장 녹화본. **UI 화면 녹화·로그 캡처본 금지**
- **명명**: 자유롭되 한글 공백 피하는 것을 권장 (예: `fire_warehouse_01.mp4`)
- **복수 파일**: 한 카테고리에 여러 파일 넣어도 됨. seed SQL에서 명시적으로 매핑

## 저장 정책

- 이 폴더 전체가 `.gitignore`에 등록됨 → **Git에 커밋되지 않음**
- 로컬 PC에만 존재. 팀원 간 공유는 별도 드라이브/클라우드로 전달
- 파일 크기 제한 없음 (각자 판단)

## 파일 추가 후 할 일

1. 파일 경로가 정확한지 확인
2. `supabase/seeds/test_cameras.sql` 의 `live_url_detail` 값을 새 경로로 업데이트
3. `supabase db query --linked -f supabase/seeds/test_cameras.sql` 재실행
4. `cd ai_agent && python main.py --once` 로 스냅샷 재생성
5. 앱에서 실시간상황 탭 → 카메라 클릭 → 현장캡쳐 확인

## 예시 파일 트리 (참고용)

```
reference_media/
├── fire/
│   ├── warehouse_ignition_01.mp4
│   └── kitchen_flame_02.mp4
├── helmet/
│   └── construction_unworn_01.mp4
├── fall/
│   ├── slip_fall_01.mp4
│   └── fall_from_height_01.mp4
├── person/
│   └── factory_walkthrough_01.mp4
└── forklift/
    └── warehouse_forklift_01.mp4
```

## ⚠ 주의

- 개인정보가 담긴 영상(얼굴 식별 가능)은 모자이크 처리 또는 사용 동의 확보 필수
- 영상은 "레퍼런스"지 "학습 데이터"가 아님 — 모델 파인튜닝은 AI-Hub 공식 데이터셋 사용
- 5월 중순 PPT 초안용 데모 목적 한정
