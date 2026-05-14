---
phase: 3
slug: vision-bbox-fusion
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-14
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | pytest 9.0.3 |
| **Config file** | none — `ai_agent/tests/` 직접 실행 (Phase 2 패턴) |
| **Quick run command** | `cd ai_agent && python -m pytest tests/test_fusion.py -x -q` |
| **Full suite command** | `cd ai_agent && python -m pytest tests/ -q` |
| **Estimated runtime** | ~5 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd ai_agent && python -m pytest tests/test_fusion.py -x -q`
- **After every plan wave:** Run `cd ai_agent && python -m pytest tests/ -q`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~5 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 1 | FUSION-01 | — | N/A | unit | `pytest tests/test_fusion.py::test_iou_xyxy -x` | ❌ W0 | ⬜ pending |
| 03-01-02 | 01 | 1 | FUSION-01 | — | N/A | unit | `pytest tests/test_fusion.py::test_collision_iou_above_threshold -x` | ❌ W0 | ⬜ pending |
| 03-01-03 | 01 | 1 | FUSION-01 | — | N/A | unit | `pytest tests/test_fusion.py::test_collision_iou_below_threshold -x` | ❌ W0 | ⬜ pending |
| 03-01-04 | 01 | 1 | FUSION-01 | — | N/A | unit | `pytest tests/test_fusion.py::test_collision_empty_list -x` | ❌ W0 | ⬜ pending |
| 03-01-05 | 01 | 1 | FUSION-02 | — | N/A | unit | `pytest tests/test_fusion.py::test_helmet_worn -x` | ❌ W0 | ⬜ pending |
| 03-01-06 | 01 | 1 | FUSION-02 | — | N/A | unit | `pytest tests/test_fusion.py::test_helmet_missing_no_helmet -x` | ❌ W0 | ⬜ pending |
| 03-01-07 | 01 | 1 | FUSION-02 | — | N/A | unit | `pytest tests/test_fusion.py::test_helmet_missing_partial -x` | ❌ W0 | ⬜ pending |
| 03-01-08 | 01 | 1 | FUSION-01+02 | — | N/A | unit | `pytest tests/test_fusion.py::test_process_fusion_3cycles -x` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `ai_agent/tests/test_fusion.py` — stubs for FUSION-01, FUSION-02, D-10 8 cases
- [ ] `ai_agent/fusion_helpers.py` — `iou_xyxy`, `hardhat_is_on`, `compute_fusion_collision`, `compute_fusion_helmet_missing`
- [ ] `ai_agent/fusion_configs.py` — `FUSION_CONFIGS` dict

*기존 `ai_agent/tests/__init__.py` — Phase 2에서 생성됨, 재사용 가능*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| fusion 알람 detection_events 적재 | FUSION-01+02 | DB 연결 필요 | `python main.py --once-detect` + SQL: `select event_type_id, camera_id, accuracy from detection_events where event_type_id in (...)` |
| helmet 단독 알람 제거 확인 | FUSION-02 (D-04) | 데몬 모드 3 cycle 필요 | 3분 run 후 `detection_events` 에서 head 단독 알람 없음 확인 |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 5s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
