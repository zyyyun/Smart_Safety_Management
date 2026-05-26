---
slug: tbm-ui-korean
type: quick
created: 2026-05-26
status: in-progress
---

# Quick Task — TBM 화면 영어 문자열 한국어 교체

## 배경

Phase 12 의 codex 자동 실행에서 TBM 관련 Kotlin UI 와 Edge Function 의 FCM body/title 일부가 영어로 작성됨.
- TBM 은 한국어 도메인 (KOSHA 가이드 기반, 도금 도메인 현장 작업자 대상)
- worker 가 받는 push notification + 카드/버튼 본문이 영어면 UX 일관성 손상
- 12-UAT.md 의 "알려진 deviation" 으로 명시한 항목 중 1개

## Scope

- `app/src/main/java/com/example/smart_safety_management/tbm/` 의 사용자 visible string literals
- `app/src/main/java/com/example/smart_safety_management/SettingOpsCatalogActivity.kt` Toast
- `app/src/main/java/com/example/smart_safety_management/MyFirebaseMessagingService.kt` 의 TBM 알림 fallback 텍스트
- `supabase/functions/notifications/index.ts` 의 tbm-start / tbm-missed / camera-down / camera-recovered FCM body/title

## Out of Scope

- 변수명·로그 메시지·디버그 출력 (개발자만 보는 부분) — 영어 유지
- 17 schema 의 OPS seed (이미 한국어로 교체됨)
- Smoke shell scripts 의 echo 메시지 (개발자만 보는 부분)

## 실행 절차

1. grep 으로 영어 string literal 식별 (TBM 관련 파일만)
2. 한국어로 교체
3. `./gradlew :app:assembleDebug` + `:app:testDebugUnitTest` 재실행 (회귀 가드)
4. `supabase functions deploy notifications` 재배포
5. SUMMARY.md 작성 + 단일 commit
