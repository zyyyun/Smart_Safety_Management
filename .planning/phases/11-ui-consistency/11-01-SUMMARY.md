---
phase: 11-ui-consistency
plan: 01
status: complete
wave: 1
requirements_validated: [UX-02]
tasks_total: 7
tasks_complete: 7
commits_total: 13
files_created:
  - app/src/main/java/com/example/smart_safety_management/ui/Tokens.kt
  - app/src/main/java/com/example/smart_safety_management/ui/components/StateCard.kt
  - app/src/main/java/com/example/smart_safety_management/ui/components/SectionHeader.kt
  - app/src/main/java/com/example/smart_safety_management/ui/components/ScreenScaffold.kt
  - app/src/main/res/layout/common_toolbar.xml
  - app/src/test/java/com/example/smart_safety_management/ui/TokensContractTest.kt
  - app/src/test/java/com/example/smart_safety_management/ui/components/StateCardTokensTest.kt
  - app/src/test/java/com/example/smart_safety_management/ui/components/SectionHeaderLabelTest.kt
  - app/src/test/java/com/example/smart_safety_management/ui/components/ScreenScaffoldConfigTest.kt
  - app/src/test/java/com/example/smart_safety_management/ui/CommonToolbarXmlTest.kt
  - app/src/test/java/com/example/smart_safety_management/tbm/TbmDashboardSsmColorsUsageTest.kt
files_modified:
  - app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardScreen.kt
  - app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardCardComposable.kt
  - app/src/main/java/com/example/smart_safety_management/tbm/TbmWorkerCardComposable.kt
  - app/src/main/java/com/example/smart_safety_management/tbm/TbmWorkerScreen.kt
  - app/src/main/java/com/example/smart_safety_management/tbm/TbmStartSection.kt
tests_passed:
  - "JVM unit (new): TokensContractTest 9/9, StateCardTokensTest 7/7, SectionHeaderLabelTest 5/5, ScreenScaffoldConfigTest 4/4, CommonToolbarXmlTest 5/5, TbmDashboardSsmColorsUsageTest 3/3 (GREEN after Task 7)"
  - "JVM unit (regression): tbm/* 기존 5종 (WorkTypeValidator + ExpectedEndAt + HazardsListReducer + TbmParticipantsReducer + SignatureState) 전체 GREEN"
  - "compileDebugKotlin: BUILD SUCCESSFUL"
  - "testDebugUnitTest (full): BUILD SUCCESSFUL"
  - "assembleDebug: BUILD SUCCESSFUL"
duration_min: 35
completed: 2026-05-27
---

# Phase 11 Plan 01: 일관 시각 언어 — 공통 컴포넌트 추출 Summary

**One-liner:** Quick #03 의 TBM 대시보드 inline 시각 token (색 6종·spacing 3종·SectionHeader Composable) 을 `ui/` 패키지 4 파일 + `res/layout/common_toolbar.xml` 1 파일 + TBM 4 Composable refactor 로 single source-of-truth 정립 — Plan 11-02 의 28+ 화면 일괄 적용 dependency 완료.

## TDD 사슬 — 13 commit (RED 6 + GREEN 6 + refactor 1)

| Task | RED commit | GREEN commit | 산출물 |
|---|---|---|---|
| 1. Tokens          | a94c464 | b6b5640 | SsmColors 6종 + SsmSpacing 3종 + SsmTypography placeholder (TokensContractTest 9/9 GREEN) |
| 2. StateCard       | af5b71d | 7a9c674 | sealed CardState 6종 + pure stateToCardTokens + @Composable StateCard (StateCardTokensTest 7/7 GREEN) |
| 3. SectionHeader   | 442ae9b | 4abd58b | sectionHeaderLabel pure + @Composable SectionHeader(icon, label, count?, iconTint) (SectionHeaderLabelTest 5/5 GREEN) |
| 4. ScreenScaffold  | baff4cb | 1eea3f6 | data class ScaffoldConfig + scaffoldConfig pure builder + @Composable ScreenScaffold (ScreenScaffoldConfigTest 4/4 GREEN) |
| 5. common_toolbar  | 6f752f6 | 354fdc6 | res/layout/common_toolbar.xml + CommonToolbarXmlTest characterization 5/5 GREEN |
| 6. Bucket D guard  | b362f0b | (Task 7 의 GREEN) | TbmDashboardSsmColorsUsageTest 3/3 — Task 7 refactor 후 GREEN 전환 |
| 7. TBM refactor    | (Task 6 의 RED)  | ca614f7 | TBM 4 Composable + TbmStartSection.kt (5 file) inline literal → SsmColors 일괄 치환, private SectionHeader → ui.components.SectionHeader import |

**TDD discipline 증거**: RED 6 commit + GREEN 6 commit + refactor 1 commit = production code (GREEN/refactor) 7 개가 **모두** 직전 RED commit 다음에 배치됨. 회귀 0.

## 회귀 가드 결과

- `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL
- `JAVA_HOME=... ./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL
- `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (13s)
- `git diff --stat ai_agent/ j2208a/ supabase/` → empty (외부 디렉터리 무영향, T-11-04 mitigation)
- `grep "Color(0xFFF59E0B)|...0xFFEF4444)" app/src/main/java/.../tbm/` → 0 hits (acceptance criteria 통과)
- `grep "private val COLOR_" app/.../tbm/` → 0 hits
- `grep "return@" app/src/main/java/.../ui/` → 0 hits (T-11-03 mitigation, b2d8745 lesson 준수)

## Deviations from Plan

### 1. [Plan defect — directory-wide acceptance grep vs <files>=4]

**Found during:** Task 7

**Issue:** Task 7 의 `<files>` 블록과 Bucket D RED test (`tbmFiles` 배열) 는 **4 file** 명시 (TbmDashboard\* + TbmWorker\*). 그러나 acceptance_criteria 의 grep 은 `app/src/main/java/com/example/smart_safety_management/tbm/*.kt` 디렉터리 전역. 두 표현이 모순 — `TbmStartSection.kt` 가 directory-wide grep 에 잡혀 acceptance 미충족.

**Fix (Rule 3 - blocking issue):** TbmStartSection.kt 의 forbidden literal 3 위치 (line 262 `Color(0xFFEF4444)`, line 329 `Color(0xFFEF4444)`+`Color(0xFF2563EB)`) 도 함께 SsmColors 로 치환. 기계적 1:1 hex 대치, 행동 변경 0. 동일 Task 7 GREEN commit 에 포함 (ca614f7).

**Files modified (initial):** 4 → **Files modified (actual):** 5 (+TbmStartSection.kt).

**Rationale:** Task 6 의 RED test (TDD Iron Law 의 contract source-of-truth) 는 변경 없이 GREEN 으로 전환됨 (4 file 만 검사). acceptance_criteria 의 directory-wide grep 은 sanity check 로 작동 — 5 file refactor 로 충족.

### 2. [Deferred — OpsEditScreen.kt 의 `private fun SectionHeader(text: String)`]

**Found during:** Task 7 acceptance_criteria grep

**Issue:** `tbm/OpsEditScreen.kt:291` 에 `private fun SectionHeader(text: String)` 정의 존재. acceptance_criteria 의 `grep "private fun SectionHeader" tbm/*.kt → 0 hits` 미충족.

**Disposition:** Deferred to Plan 11-02 또는 후속 cleanup.

**Rationale:** Signature 가 ui.components.SectionHeader(icon, label, count, iconTint) 와 완전히 달라 (text 1 인자만), 단순 교체 불가. 호출처에 icon 결정 + label/count 분리 결정 필요 — plan scope expansion. Advisor 권고대로 deferred. Plan 11-02 Sub-task 1 (Home 카드) 또는 별도 cleanup task 에서 처리.

### 3. [Minor — StateCard.kt 의 주석 안 `return@<Lambda>` 패턴 발견 및 변환]

**Found during:** Task 2 acceptance_criteria grep

**Issue:** docstring 안 "Compose `return@<Lambda>` 금지" 문구가 plan grep `return@` 0 건 검증에서 1 hit 발생. 실제 코드는 아니지만 grep 은 패턴만 체크.

**Fix:** Docstring 을 "Compose 람다 early-exit 금지 (b2d8745 lesson)" 로 표현 변경. 의미 동일, grep 통과.

## Auth gates

해당 없음 (autonomous TDD plan, 외부 인증 의존성 없음).

## Known Stubs

해당 없음 — 본 plan 은 컴포넌트 추출 + characterization. 모든 신규 API 는 즉시 사용 가능 (Plan 11-02 가 호출). SsmTypography 는 의도적으로 빈 object (Phase 11 out of scope, future hook 로 명시).

## Threat Flags

해당 없음 — 본 plan 은 UI/시각 token 추출. 보안 surface 변경 0.

## TDD Gate Compliance

본 plan 은 `type: tdd`. 13 commit 의 git log 검증:

1. RED gate: 6개 test commit (a94c464, af5b71d, 442ae9b, baff4cb, 6f752f6, b362f0b)
2. GREEN gate: 6개 production commit (b6b5640, 7a9c674, 4abd58b, 1eea3f6, 354fdc6, + Task 7 의 refactor commit ca614f7 가 Task 6 RED 의 GREEN)
3. 모든 GREEN commit 이 직전 RED commit 다음 위치 — TDD Iron Law (사용자 directive 2026-05-27) 완전 준수.

## Self-Check: PASSED

- 모든 files_created 11 파일 존재 확인 (`ls` verified)
- 모든 13 commit hash git log 에서 확인 (`git log --oneline -16`)
- testDebugUnitTest + compileDebugKotlin + assembleDebug 3종 모두 BUILD SUCCESSFUL
- 외부 디렉터리 (ai_agent/j2208a/supabase) diff 0
- Wave 2 (Plan 11-02) 진행 가능 — 본 plan 의 5 신규 산출물이 Plan 11-02 의 dependency 완전 충족
