# Phase 12 Discussion Log

**Date:** 2026-05-23
**Phase:** TBM 재설계 (KOSHA 가이드 흡수)
**Mode:** discuss (default), gsd-sdk 미설치라 수동 진행

---

## Selection (D6)

User selected: Areas A, B, C, E (4 areas, all gray areas chosen for deep discuss).
Area D (회의록 출력/보고서) deferred to planning phase per dispatch — not core decision.

---

## D7 — Area A-1: OPS prefill 수준

**Q:** TBM 세션 시작 시 작업 종류 선택하면 위험·대책 칸이 어떻게 채워지나?

**Options:**
- A) 먼저 채워두고 사용자가 고치기 (하이브리드)
- B) 옆에 참고만 보이고 칸은 빔
- C) 채워두되 수정 불가
- D) OPS 무관, 100% 자유 입력

**Choice:** A (하이브리드)

**Rationale:** 매일 하는 사용자 부담 절감 + 가이드의 "TBM 은 마지막 위험성평가" 원칙 절충. 자체 추가 항목은 `is_custom=true` 태그로 구분.

---

## D8 — Area A-2: OPS template 깊이

**Q:** 우리 OPS template 이 KOSHA 가이드 양식을 어디까지 닮을까?

**Options:**
- A) 핵심만 (잠재위험·대책·핵심조치)
- B) 핵심 + 자율점검 (yes/no 체크)
- C) 핵심 + 자율점검 + 4 카테고리 구분
- D) 자체 설계 — 가이드 양식 무관

**Choice:** B (핵심 + 자율점검)

**Rationale:** 가이드의 핵심 양식 흡수, 단 4 카테고리 (관리적 사항 / 안전장치 / 작업 중 / 준수사항) 분류는 UI 복잡도 대비 가치 부족. Phase 9 의 `tbm_templates.checklist_template` JSONB 를 자율점검 컨테이너로 재활용.

---

## D9 — Area A-3: TBM ↔ AI 비전 연동

**Q:** TBM 세션에 '오늘 지게차 작업 있음' 등록되면 AI 비전 시스템과 어떻게 연동?

**Options:**
- A) 단순 기록만 (v1.1)
- B) 알림 우선순위 hint
- C) detector conf threshold 동적 조정
- D) 양방향 — 비전이 TBM 에도 영향

**Choice:** A (단순 기록만)

**Rationale:** v1.1 은 work_scope DB 기록만. v1.2 에서 'work_scope 별 detection_events 교차 조회 대시보드' 같은 후속.

---

## D10 — Area B-1: 마이그레이션 path

**Q:** Phase 9 기존 tbm_* 테이블 + 시드 데이터 처리?

**Options:**
- A) Destructive — drop + recreate
- B) ALTER + 기존 보존
- C) 혼합 — 세션은 ALTER, templates 는 destructive
- D) 병렬 — tbm2_* 신설

**Choice:** A (Destructive)

**Rationale:** 새 `014_tbm_v2_schema.sql` 에서 기존 4 테이블 DROP + RECREATE. 운영 DB 의 기존 row 적어 안전. generic 5 templates (fire/electric/general/heavy/height) 가 도금 도메인에 안 맞아 noise.

---

## D11 — Area B-2: Home 의 TBM UI

**Q:** Home 에서 TBM UI 가 어떻게 보일까?

**Options:**
- A) 요약만 표시, 자세한 건 TbmDashboard 진입
- B) Home 안에 N 세션 list 직접 표시
- C) 세션 수에 따라 다르게 (N≤1 카드, N≥2 요약)
- D) Home 에서 제거, TBM 은 메뉴 안에만

**Choice:** A (요약만 표시)

**Rationale:** Home 공간 절약 + 다른 카드 (워치/카메라/일일점검/알림) 와 일관 시각 언어 유지. Phase 11 의 UX-02 (Home 카드 일관) 와 통합 디자인.

---

## D12 — Area C-1: 관리자 OPS 카탈로그 UI 위치

**Q:** 관리자가 OPS 카탈로그를 관리하는 UI 위치?

**Options:**
- A) 설정 메뉴 안 신규 항목
- B) TBM Dashboard 안 모달
- C) 별도 Activity 신설
- D) Home 의 카드로

**Choice:** A (설정 메뉴 안 신규 항목)

**Rationale:** 기존 Setting* 시리즈와 일관 패턴. SettingOpsCatalogActivity 신규.

---

## D13 — Area C-2: 신규 OPS 작성 시 필수 필드

**Q:** 관리자가 신규 OPS 추가할 때 어떤 필드까지 필수?

**Options:**
- A) 핵심 필수 + 나머지 선택
- B) 모든 필드 필수
- C) 이름만 필수
- D) 가이드 OPS import 기능

**Choice:** A (핵심 필수 + 나머지 선택)

**Rationale:** 필수 = 이름 + 잠재위험 list + 대책 list. 자율점검·핵심조치·detector hint 는 선택.

---

## D14 — Area E-1: 리더 모델

**Q:** 같은 그룹이 하루 여러 세션을 할 때 리더 모델?

**Options:**
- A) 둘 다 허용
- B) 한 리더만 여러 세션
- C) 세션마다 반드시 다른 리더
- D) 관리자만 리더 가능

**Choice:** A (둘 다 허용)

**Rationale:** 현장 자유도 보존. 단 권한 가드는 `user_role IN ('manager','general_manager')` 유지 (worker 는 리더 불가).

---

## D15 — Area E-2: 작업자 참여 모델

**Q:** 한 작업자가 하루 여러 세션에 참여 가능한가?

**Options:**
- A) N 세션 참여 가능
- B) 하루 1 세션 참여만
- C) 공정별로 자동 할당
- D) 제한 없음 (A 와 의미 동일)

**Choice:** A (N 세션 참여 가능)

**Rationale:** 같은 사람이 오전 산세 + 오후 도금 + 야간 검사 다 가능. `tbm_participants UNIQUE (session_id, user_id)` 유지 (같은 세션 중복만 차단).

---

## Deferred Items (planning 단계 또는 v1.2)

- `work_scope` 컬럼 type (TEXT vs ENUM) — planner 결정
- 잠재위험·대책 list schema (JSONB array vs child table) — planner 결정
- SLAM 행동요령 UI 표시 방식 (모달·tooltip·inline) — Phase 11 token 확정 후
- OPS 비활성 시 진행 중 세션 참조 보존 (snapshot vs live) — planner 결정 (Phase 9 의 tbm_checklists session-snapshot 패턴 재활용 권장)
- 회의록 PDF/이미지 export — v1.2
- detector hint 동적 연계 — v1.2

---

## Encoding 이슈 처리 (2026-05-23 운영 메모)

이 discussion 진행 중 AskUserQuestion 의 한글이 `\uXXXX` escape 시 4회 깨짐 발생 (D6·D7·D10·D11). 5번째 깨짐 후 CLAUDE.md 에 운영 규칙 5개 추가 (description 60자 이내 / 한자·특수문자 금지 / 자동 escape 차단 self-check / 영어 fallback / 옵션 4개 제한).
