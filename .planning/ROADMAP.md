# Smart Safety Management - Roadmap

> Living roadmap. Completed milestone details are archived under `.planning/milestones/`.

---

## Milestones

- [x] **v1.0 - 5월 PPT 데모** - scope-reduced shipped 2026-05-22. Archive: `.planning/ROADMAP.v1.0.md`, `.planning/REQUIREMENTS.v1.0.md`.
- [x] **v1.1 - 앱 전체 완성도** - scope-reduced shipped 2026-06-04. Archive: `.planning/milestones/v1.1-ROADMAP.md`, `.planning/milestones/v1.1-REQUIREMENTS.md`, `.planning/milestones/v1.1-MILESTONE-AUDIT.md`.
- [ ] **Next milestone** - start with `$gsd-new-milestone` when ready.

---

## Completed Milestone Details

<details>
<summary>v1.1 앱 전체 완성도 - SHIPPED scope-reduced 2026-06-04</summary>

### Outcome

v1.1 is closed as a deliberate scope-reduced milestone. It delivered substantial product stabilization work, but not every originally scoped requirement was formally verified.

### Phase Status at Close

| Phase | Name | Close Status |
|---:|---|---|
| 11 | 일관 시각 언어 정립 | complete by summaries, missing formal verification |
| 12 | TBM 재설계 | partial, implementation and DB/Edge evidence present, missing formal verification |
| 13 | 데이터 신뢰성 + 정보구조 | quick fixes landed, no phase artifact |
| 15 | ai_agent Docker 컨테이너화 | replaced in practice by visible PowerShell task scheduler approach, not formally amended |
| 14 | 6월 설치 사전 UAT | not completed |

### Known Gaps Accepted at Close

- Formal `*-VERIFICATION.md` files are missing for v1.1 phases.
- Phase 13/14/15 directories were not created.
- Docker scope remains unresolved because operational work pivoted to visible PowerShell scheduled startup.
- UAT artifacts for 검단/포천 environment and 3-role walkthrough were not created.

See `.planning/milestones/v1.1-MILESTONE-AUDIT.md` for the full audit.

</details>

<details>
<summary>v1.0 5월 PPT 데모 - SHIPPED scope-reduced 2026-05-22</summary>

See `.planning/ROADMAP.v1.0.md` and `.planning/REQUIREMENTS.v1.0.md`.

</details>

---

## Current Planning State

No active milestone is open.

Start the next milestone with:

```text
$gsd-new-milestone
```
