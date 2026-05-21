# 이미지 업로드 수동 검증 체크리스트 (Next-2)

**대상 브랜치**: `test` (버그 수정 커밋 포함)
**검증 목적**: 4개 Storage 버킷 × 생성/수정/완료 플로우 × 업로드 실패 경로 전수 확인.
특히 B1(kept Map overwrite) · B2(update 라우트 시그니처) 수정 효과 검증.

## 사전 준비

1. Android Studio **Build → Clean Project → Rebuild**
2. testuser1 (또는 younseu) 관리자 계정으로 실기기 앱 로그인
3. Logcat 필터 `RetrofitUpload` 탭 하나 열어둘 것 (D1 로그 확인용)
4. Supabase SQL 접근 준비 — 각 시나리오 쿼리 복붙 실행

## 체크리스트 (13항목)

### profile-images 버킷

#### [ ] P1. 프로필 새 이미지 → 앱 재시작 후에도 유지
- 액션: 내 프로필 화면 → 사진 변경 → 갤러리에서 1장 선택 → 저장
- 기대:
  - Toast "저장 성공"
  - Storage `profile-images/` 에 새 객체 1개
  - `profiles.profile_image_url` 업데이트
  - 앱 완전 종료 → 재실행 → 프로필 화면에 동일 이미지 렌더
- 확인 SQL:
  ```sql
  SELECT user_id, name, profile_image_url, updated_at
    FROM profiles WHERE user_id = 'testuser1';
  ```

#### [ ] P2. 프로필 업로드 중 네트워크 차단 (D1 검증)
- 액션: Wi-Fi/모바일 끈 상태에서 프로필 이미지 저장 시도
- 기대:
  - Toast 실패 안내 노출 (앱에서 이미 onFailure Toast 구현됨)
  - Logcat `RetrofitUpload` 태그에 `storage upload failed ... code=...` 로그
  - `profiles.profile_image_url` 이전 값 그대로 (업데이트 되면 안 됨)

---

### action-images 버킷

#### [ ] A1. 조치 요청 이미지 0장 생성
- 액션: AI감지 상세 → 조치요청 → 이미지 0장으로 제출
- 기대:
  - Toast 성공
  - `action_requests` 1행, `action_images` 0행
- 확인 SQL:
  ```sql
  SELECT ar.request_id, ar.created_at, COUNT(ai.image_id) AS img_cnt
    FROM action_requests ar
    LEFT JOIN action_images ai ON ai.request_id = ar.request_id
    WHERE ar.request_id = (SELECT MAX(request_id) FROM action_requests)
    GROUP BY ar.request_id, ar.created_at;
  ```

#### [ ] A2. 조치 요청 이미지 1장
- 액션: 조치요청 생성 시 이미지 1장 첨부
- 기대: `action_images` 1행, 조치 상세 화면에 1장 렌더
- 확인 SQL: A1 쿼리와 동일 (`img_cnt=1` 예상)

#### [ ] A3. 조치 요청 이미지 5장
- 액션: 5장 선택하여 제출
- 기대:
  - `action_images` 5행
  - ActionDetail_worker 상세 화면에 5장 모두 렌더
  - Storage `action-images/` 에 5개 신규 객체

#### [ ] A4. 조치 완료 시 이미지 3장 추가
- 액션: 기존 A3 조치에 대해 완료 처리 시 이미지 3장 추가
- 기대: `action_images` 에 기존 5행 + 3행 = 8행
- 확인 SQL: A1 쿼리에서 `img_cnt=8`

---

### check-images 버킷 (B1+B2 핵심 검증)

#### [ ] D1-new. 신규 일일점검 이미지 3장
- 액션: 일일점검 생성 화면에서 location/hazard/countermeasure 입력 + 이미지 3장 선택
- 기대:
  - `daily_safety_check` 1행, `check_images` 3행
  - DailyList 에 3장 렌더
- 확인 SQL:
  ```sql
  SELECT c.id, c.location, c.check_date, COUNT(ci.id) AS img_cnt,
         array_agg(ci.image_url) AS urls
    FROM daily_safety_check c
    LEFT JOIN check_images ci ON ci.check_id = c.id
    WHERE c.id = (SELECT MAX(id) FROM daily_safety_check)
    GROUP BY c.id, c.location, c.check_date;
  ```

#### [ ] D2-edit-mixed. **핵심 버그 수정 시나리오** — 기존 2장 유지 + 새 1장 추가
- 전제: D1-new 의 3장짜리 점검이 존재
- 액션: 그 점검 수정 → 기존 3장 중 **1장 삭제**, 2장 유지 → 새 이미지 **1장 추가** → 저장
- 기대:
  - `check_images` 최종 **3행** (유지 2 + 신규 1)
  - 삭제한 1장의 URL 은 DB 에 없어야 함
  - 유지한 2장 URL 은 그대로 유지
  - 신규 1장 URL 은 새로 insert
- **수정 전에는**: `check_images` 가 1행만 남았을 것 (kept 1개만 통과, new 0개 insert 스킵)
- 확인 SQL: D1-new 쿼리와 동일

#### [ ] D3-edit-shrink. 기존 3장 중 1장만 유지
- 액션: 수정 시 2장 삭제, 1장 유지, 새 이미지 0장
- 기대: `check_images` 최종 1행 (유지한 1장만)

#### [ ] D4-complete. 기존 점검 완료 처리 시 이미지 2장 추가
- 액션: 기존 점검에 대해 "완료" 액션 + 이미지 2장 첨부
- 기대: `check_images` 에 기존 + 2행 (completion 루트는 `image_urls` 단일 배열 — 회귀 확인 목적)
- 확인 SQL: D1-new 쿼리

---

### camera-captures 버킷 (회귀 확인)

#### [ ] C1. RealTime CCTV 캡처 목록 렌더
- 액션: 실시간상황 탭 → CCTV 카드 클릭 → 최근 캡처 목록
- 기대: Next-3/Next-4 로 이미 들어있는 PERIODIC/DETECTION 캡처들 정상 표시
- 변경 사항 영향 없음 — 회귀 확인용

---

### 실패 경로 검증 (D1)

#### [ ] E1. 업로드 실패 시 DB 행 생성 안 됨
- 액션 옵션 A: 기기 비행기 모드 ON → 일일점검 3장 업로드 시도
- 액션 옵션 B: `/functions/v1/upload?bucket=invalid_xxx` 등 잘못된 bucket 으로 임시 코드 변경 후 빌드
- 기대:
  - Retrofit `onFailure` 발동 → Toast 실패 노출
  - `daily_safety_check` 에 텍스트 행 생성 **안 됨**
  - Storage 에 일부만 올라간 orphan 이 생길 수는 있음 (후속 정리 대상)
- 수정 전에는: 텍스트만 담긴 JSON 이 Edge Function 에 도달 → DB 행은 생성 + 이미지는 0장

#### [ ] E2. Logcat 실패 로그 형식 확인
- E1 상황에서 Logcat 에 아래 형식 로그 출력 확인:
  ```
  E RetrofitUpload : storage upload failed field=images bucket=check-images code=... body=...
  ```
- 또는 응답 파싱 실패 시:
  ```
  E RetrofitUpload : upload response parse failed: ...
  ```

---

## 완료 후 기록 방법

각 [ ] 앞에 결과 마크:
- `✅` 통과
- `⚠️` 부분 통과 (메모 필수)
- `❌` 실패 (재현 단계 · 로그 첨부)

13항목 중 최소 D2-edit-mixed, A3, P1, E1 네 가지는 반드시 통과해야 Next-2 수용 기준 만족.

## 관련 파일

- 수정: [RetrofitClient.kt](../../app/src/main/java/com/example/smart_safety_management/RetrofitClient.kt)
- 호출부 (변경 없음):
  - [DailyList.kt](../../app/src/main/java/com/example/smart_safety_management/DailyList.kt) L363-412
  - [SettingProfileActivity.kt](../../app/src/main/java/com/example/smart_safety_management/SettingProfileActivity.kt) L234-299
  - [ActionDetailViewModel.kt](../../app/src/main/java/com/example/smart_safety_management/ActionDetailViewModel.kt) L79-110
- Edge Function (변경 없음):
  - [daily-checks/index.ts](../../supabase/functions/daily-checks/index.ts)
  - [actions/index.ts](../../supabase/functions/actions/index.ts)
  - [users/index.ts](../../supabase/functions/users/index.ts) (profile update)
  - [upload/index.ts](../../supabase/functions/upload/index.ts)
