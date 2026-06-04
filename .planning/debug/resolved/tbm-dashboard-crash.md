---
slug: tbm-dashboard-crash
status: resolved
trigger: "TBM 대시보드 진입 시 앱이 꺼짐 — quick-03 (commit e557fb4) redesign 적용 직후"
created: 2026-05-26
updated: 2026-05-27
resolved: 2026-05-27
root_cause: "GroupSessionsSection 의 `return@Column` early-exit 가 Compose group stack 깨뜨림 (ArrayIndexOutOfBoundsException at IntStack.peek2)"
fix: "if (empty) { ...; return@Column } 패턴을 if/else 양분으로 교체"
files_changed: ["app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardScreen.kt"]
---

# Debug — TBM 대시보드 진입 시 앱 종료

## Symptoms

- **What:** 매니저 (testuser1) 로 로그인 → 홈 → TBM 카드 클릭 또는 TBM 대시보드 화면 진입 시 앱이 꺼짐 (process death)
- **When:** quick-03 commit `e557fb4` (TbmDashboardScreen.kt 전체 rewrite) 적용 후 처음 발생
- **Before:** dede96a / 477ac38 시점에는 TBM 대시보드 정상 진입 (codex 의 이전 디자인은 ugly 했지만 crash X)
- **Repro:** 매니저 dev picker → testuser1 → HomeActivity → TBM 카드 탭 → 즉시 종료 (예상)

## Current Focus

- **hypothesis:** Compose recomposition / runtime 안 ImageVector loading / 새 `GroupSessionsSection` 의 `return@Column` 조건부 emit 중 하나로 인한 RuntimeException
- **test:** logcat 의 마지막 `AndroidRuntime FATAL EXCEPTION` stack trace 확인
- **expecting:** Throwable type + 첫 stack frame (`com.example.smart_safety_management.tbm.TbmDashboardScreen*Kt.*`) 으로 root cause 즉답 가능
- **next_action:** 사용자에게 logcat stack trace 요청

## Hypotheses (사전 가설 — 우선순위 순)

### H1: Material Icons runtime class loading 실패 (DEMOTED)
- **Status:** 가능성 낮음 — material-icons-extended 의존성 확인됨 (app/build.gradle.kts:127)
- **Reasoning:** 만약 missing 이었으면 compile 시 unresolved reference. assembleDebug BUILD SUCCESSFUL 통과했고 7 Material Icons 모두 import 정상. APK 안에 class 들이 들어있다고 가정 가능.

### H2: `return@Column` 조건부 early-exit 가 Compose recomposition 깨뜨림
- **Status:** 가능성 중간
- **Reasoning:** `GroupSessionsSection` 안 `if (activeSessions.isEmpty() && endedSessions.isEmpty())` 분기에서 `return@Column` 호출. Compose 의 @Composable 람다 안에서 조건부 early-return 은 일반적으로 OK 지만, recompose 시 emit child count 가 변하면 group 트리 인덱싱 문제 가능.
- **Test:** logcat 에 `IllegalStateException` + Compose 의 `SlotTable` / `Composer` 관련 stack frame 있으면 확정.

### H3: Realtime callbackFlow shape mismatch (이전 데이터로 캐시된 상태)
- **Status:** 가능성 낮음
- **Reasoning:** TbmRepository 변경 0. 새 TbmDashboardScreen 은 동일 flow API 호출. 그러나 Realtime 채널이 이전 세션 (testuser1) 으로 살아있고 새 화면 진입 시 callback 이 다른 thread 에서 emit → recomposition 충돌 가능.
- **Test:** logcat 에 `IO`, `Postgrest`, `Realtime` 관련 stack frame.

### H4: 단순 APK 미배포 — 사용자가 본 crash 는 이전 dirty state
- **Status:** 가능성 낮지만 검증 필요
- **Reasoning:** `./gradlew installDebug` 실행 안 하면 단말의 APK 는 이전 빌드 (codex 본문 + mojibake 정리만 됐던 dede96a 시점) 일 수 있음. 다만 이전 빌드에서도 crash 났는지는 사용자 보고 없음 (직전 메시지가 "구려" 였고 "꺼짐" 은 처음 보고).
- **Test:** `adb shell dumpsys package com.example.smart_safety_management | grep versionCode` 로 단말 APK version 확인.

## Evidence

(채워질 예정 — logcat / stack trace / dumpsys 출력 받으면 추가)

## Eliminated

(아직 없음)

## 사용자에게 필요한 정보

가장 빠른 root cause 진단을 위해 logcat 출력 1회 필요.

### 옵션 A — Android Studio Logcat (가장 쉬움)

1. 단말 USB 연결 + Android Studio 열기
2. View → Tool Windows → Logcat
3. 필터 입력란에: `package:mine level:error`
4. 앱 실행 → 매니저 로그인 → TBM 대시보드 진입 → 꺼짐
5. Logcat 의 빨간 줄 (FATAL EXCEPTION) 부터 stack trace 끝까지 (보통 30-50 줄) **복사해서 알려주세요**

### 옵션 B — adb 명령어 (PowerShell / WSL)

```powershell
# 단말 연결 후
adb logcat -c                                              # 기존 로그 비우기
# 앱에서 매니저 로그인 → TBM 대시보드 진입 (crash 까지)
adb logcat -d AndroidRuntime:E *:S > crash.log             # FATAL 만 추출
type crash.log
```

또는 한 줄로:
```powershell
adb logcat -d "AndroidRuntime:E" "*:S"
```

### 옵션 C — 단말의 "최근 크래시" 알림 클릭

Android 가 crash 후 "앱이 응답하지 않습니다" 알림을 띄우면 "보고" 클릭 시 일부 stack trace 보임. screenshot 가능.

## 진단 후 예상 흐름

- Stack trace 받음 → 위 H2/H3/기타 가설 확정 → 1-2 line fix → build + test → commit
- 만약 H4 (APK 미배포) 라면 → `./gradlew :app:installDebug` 한 번으로 해결

## Resolution

### Stack trace 결정적 단서 (2026-05-27 10:56)

```
java.lang.ArrayIndexOutOfBoundsException: length=320; index=-2
    at androidx.compose.runtime.IntStack.peek2(Stack.kt:52)
    at androidx.compose.runtime.ComposerImpl.end(Composer.kt:2601)
    at androidx.compose.runtime.ComposerImpl.endGroup(Composer.kt:1797)
    at androidx.compose.runtime.ComposerImpl.endRoot(Composer.kt:1667)
    at androidx.compose.runtime.ComposerImpl.doCompose(Composer.kt:3610)
    at androidx.compose.runtime.ComposerImpl.recompose$runtime_release(Composer.kt:3552)
```

`IntStack.peek2()` 에 `index=-2` → group end 가 group start 없이 호출됨 → SlotTable 깨짐.

추가 단서: 이전 line 의 `Compiler allocated 10MB to compile ... TbmStartSection` — TBM 대시보드 진입 직후 JIT 가 TbmStartSection 컴파일 중 crash 발생. 즉 TbmDashboardScreen → GroupSessionsSection emit 도중 깨짐.

### Root cause

**`return@Column` 으로 `@Composable Column { }` 람다에서 early-exit.**

Compose 런타임 은 `Column { content }` 람다가 시작 시 `startGroup()` + 종료 시 `endGroup()` pair 를 호출. 람다 안에서 `return@Column` 으로 도중 종료하면 일부 child Composable 의 group start 만 호출되고 endGroup 호출 시 stack 이 비어 `IntStack.peek2()` 가 `length=320, index=-2` (음수) 로 OOB.

**문제 코드 (이전):**

```kotlin
Column(modifier = Modifier.fillMaxWidth()) {
    Text("그룹 #${group.groupId} (${group.inviteCode})", ...)

    if (activeSessions.isEmpty() && endedSessions.isEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text("오늘 TBM 세션 없음", ...)
        return@Column   // ← 여기가 ComposerImpl.endRoot 깨뜨림
    }

    if (activeSessions.isNotEmpty()) { ... }
    if (endedSessions.isNotEmpty()) { ... }
}
```

**언제 trigger:** `fetchGroupsForManager()` 가 빈 그룹 (sessions 없는 그룹) 을 1개라도 반환하거나, 첫 진입 시 `sessionsByGroup = emptyMap()` 상태에서 모든 group 의 active/ended 가 빈 list → `return@Column` 발동 → crash. 사용자 testuser1 의 첫 진입 시점에는 sessionsByGroup 이 비어있는 frame 이 잠시 있고 그때 즉시 crash.

### Fix

`if (empty) { ...; return@Column }` 패턴을 **`if/else` 양분 구조** 로 교체. early-return 제거.

```kotlin
Column(modifier = Modifier.fillMaxWidth()) {
    Text("그룹 #${group.groupId} (${group.inviteCode})", ...)

    if (activeSessions.isEmpty() && endedSessions.isEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text("오늘 TBM 세션 없음", ...)
    } else {
        if (activeSessions.isNotEmpty()) { ... }
        if (endedSessions.isNotEmpty()) { ... }
    }
}
```

Compose 의 control flow 는 if/else / when 만 안전. 람다 중간 return 은 금지 (group stack invariant 깨짐).

### 검증

| Task | 결과 |
|---|---|
| `return@Column` 본문 잔존 | **0건** ✅ (`return@LaunchedEffect` 만 line 82 에 정상 잔존 — suspend lambda) |
| `:app:assembleDebug` | ✅ BUILD SUCCESSFUL (55s) |
| `:app:testDebugUnitTest` | ✅ 54/54 cases PASS 유지 |

### 부수 관찰 (별건)

logcat 첫 줄에 또 다른 warning:
```
Supabase-Core E  GET request to endpoint /rest/v1/safety_alerts failed with exception
                  The coroutine scope left the composition
```

이건 Phase 7 (watch/safety_alerts) 의 Realtime 채널이 화면 leave 시 coroutine scope cancel 되면서 in-flight HTTP request 가 취소된 정상 cleanup. Crash 와 무관. Logging level Error 라서 눈에 띄지만 실제로는 expected behavior (Realtime scope-bound cleanup).

### Lesson (장기 기억)

`@Composable` 람다 안에서 `return@<Lambda>` 는 금지. 항상 `if/else` 양분 또는 `when` 으로 분기. Compose 의 group/slot 트리는 람다 끝까지 emit 가정.

이번 redesign 에서 `GroupSessionsSection` 의 "오늘 세션 없음" 분기를 빠르게 early-return 으로 처리했는데, 같은 패턴이 Java 에서는 OK 지만 Compose 에서는 silent crash. 컴파일러가 잡지 못함 — 런타임 ART 의 IntStack OOB 로만 발현.

### 영향 파일

- `app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardScreen.kt` (단일 파일, GroupSessionsSection 11 line 변경)

