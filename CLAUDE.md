# Smart Safety Management — Claude 작업 지침

## 한글 (CJK) AskUserQuestion 처리 — MUST READ

**문제**: 세션 중 AskUserQuestion 의 `question` / `option.label` / `option.description` 에 한글이 들어갈 때 `\uXXXX` escape 형태로 입력하면 codepoint 가 자주 깨져 사용자에게 "한글이 깨졌다" 로 보이는 사례가 반복됨 (2026-05-23 v1.1 milestone 작업 중 5회 이상 발생).

**원인**: 모델이 긴 CJK 문자열을 `\uXXXX` 로 reflexive escape 하려 할 때 codepoint 를 잘못 회상해서 다른 문자가 됨 (예: `㄃` 을 管 U+7BA1 으로 의도했지만 실제로는 ㄃ U+3103).

**규칙 (gstack/office-hours skill 의 AskUserQuestion Format 12번 항목과 동일)**:

- ✅ 한글·한자·일본어 등 non-ASCII 문자는 **직접 UTF-8 로 입력**. Claude Code 의 tool parameter pipe 는 UTF-8 native 라 escape 불필요.
- ❌ `\uXXXX` escape 절대 사용 금지.
- ✅ JSON 필수 escape (`\n` / `\t` / `\"` / `\\`) 만 사용.

**예시**:
```
나쁨:  "question": "다음 설정"
좋음:  "question": "다음 설정"
```

**Validation**: AskUserQuestion 호출 직전 self-check — `\u` 문자열이 question/label/description 에 있으면 즉시 직접 한글로 재작성.

**추가 운영 규칙 (2026-05-23 5번째 깨짐 후 확정)**:

1. AskUserQuestion 의 `description` 은 **한 줄, 60자 이내** 유지. 길수록 후반부 codepoint 환각률 폭증.
2. Description 안에 `·`, `①`, `❶`, 한자, 한국어 외 CJK 문자 **금지**. 모델이 비슷한 글리프 다른 codepoint 로 자주 잘못 입력.
3. 송신 직전 self-check: JSON 안 `\u` 로 시작하는 CJK escape (`\uac` ~ `\ud7`) 가 있으면 보내지 말고 직접 한글로 재작성. 자동 escape fallback 차단.
4. 위 1~3 으로도 깨지면 — description 영어 한 줄로, label 만 한글. (정보 손실보다 mojibake 가 더 나쁨.)
5. 옵션 5개 이상 필요 시 — 첫 4개만 보내고 "Other" 선택 시 별도 freeform 으로.

**검증 패턴**: 평소 디버깅처럼 보내기 전에 outputs 의 일부 한글이 의미 있는 단어인지 한 번 더 확인. 옵션 descriptions 후반부에 "그·래", "주·결", "이·ㅇ" 같은 1-2자 분절이 있으면 깨진 것.

## 한글 인코딩 환경 컨텍스트

- 프로젝트 경로 자체에 한글 포함: `D:\2026_산업안전\Smart_Safety_Management\`
- Windows ACP=CP949 + PowerShell 5.1 ANSI 표시 → bash 출력의 한글이 CRLF/encoding 영향 받음
- 빌드 output 은 D:/ssm-app-build 로 redirect (app/build.gradle.kts:107, JEP 400 회피 워크어라운드)
- 커밋 메시지의 한글은 일반적으로 OK (git 의 UTF-8 처리)

## 프로젝트 구조 (요약)

- `.planning/` — GSD 워크플로우 (PROJECT.md / REQUIREMENTS.md / ROADMAP.md / STATE.md / MILESTONES.md / phases / quick / explorations)
- `app/` — Android Kotlin/Compose (manager · worker · general_manager 역할)
- `ai_agent/` — Python YOLO scheduler (flat package, 절대 import)
- `j2208a/` — J2208A BLE 워치 어댑터 (Python)
- `supabase/` — migrations + Edge Functions (Deno)
- `scripts/` — 시연·운영 자동화 (PowerShell + Python)
- `reference_media/` — 검증·시연용 비디오 / inference 결과

## 현재 활성 milestone

**v1.1 앱 전체 완성도** (started 2026-05-23) — 4 phases (11·12·13·14), 13 requirements, 검단·포천 6월 설치 전 마감.

- Phase 11: 일관 시각 언어 정립
- Phase 12: TBM 재설계 (KOSHA 가이드 흡수) ← **진행 중 discuss-phase**
- Phase 13: 데이터 신뢰성 + 정보구조
- Phase 14: 6월 설치 사전 UAT

## v1.0 historical (SHIPPED 2026-05-22)

`.planning/ROADMAP.v1.0.md` + `.planning/REQUIREMENTS.v1.0.md` + `.planning/MILESTONES.md` 의 v1.1 Shipped 엔트리 참조.

## 외부 도구 상태

- `gsd-sdk` **미설치** (Windows 환경) — GSD 명령어들은 모두 수동 처리. 파일 직접 edit + commit.
- `bun`, `bunx.exe` — 정상
- `pdftotext` (poppler) — 사용 가능 (PDF 추출 시 `-layout -enc UTF-8 -f N -l M` 옵션)
- `python` — miniconda3 경로
- `psql` — 미설치 (Supabase 작업 시 PostgREST + Management API 우회)
- `pdftoppm` — 미설치 (Read tool 의 PDF pages 파라미터 실패 → pdftotext 로 우회)
