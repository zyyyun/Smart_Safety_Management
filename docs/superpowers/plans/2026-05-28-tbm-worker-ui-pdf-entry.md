# TBM Worker UI and PDF Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the approved A-style worker TBM UI, automatic OPS-derived session names, and completed-session PDF export entry point.

**Architecture:** Add small pure display helpers for session naming/grouping, then reuse them from manager start, manager dashboard, and worker screen. Keep PDF generation out of scope; only expose a disabled/placeholder UI action for completed sessions.

**Tech Stack:** Kotlin, Jetpack Compose Material3, existing Supabase TBM repository and Edge Function request models, JUnit unit tests.

---

### Task 1: Display Helpers and Tests

**Files:**
- Modify: `app/src/main/java/com/example/smart_safety_management/tbm/TbmModels.kt`
- Test: `app/src/test/java/com/example/smart_safety_management/tbm/TbmDisplayNameTest.kt`

- [ ] Add tests for OPS-derived names and session display names.
- [ ] Implement `selectedOpsSessionTitle()` and `tbmSessionDisplayTitle()`.
- [ ] Run `:app:testDebugUnitTest --tests com.example.smart_safety_management.tbm.TbmDisplayNameTest`.

### Task 2: Manager Start UI

**Files:**
- Modify: `app/src/main/java/com/example/smart_safety_management/tbm/TbmStartSection.kt`

- [ ] Remove the work-scope text field.
- [ ] Show a read-only derived session name from selected OPS titles.
- [ ] Send the derived name as `TbmStartRequest.workScope`.
- [ ] Keep location, memo, expected end time, and OPS multi-select.

### Task 3: Worker A Layout

**Files:**
- Modify: `app/src/main/java/com/example/smart_safety_management/tbm/TbmWorkerScreen.kt`

- [ ] Wrap worker content in a scrollable body.
- [ ] Add a bottom action area with signature and submit controls for not-yet-joined active sessions.
- [ ] Group hazards, controls, and checklist items by OPS title.
- [ ] Increase worker font sizes slightly.

### Task 4: Completed Session PDF UI Entry

**Files:**
- Modify: `app/src/main/java/com/example/smart_safety_management/tbm/TbmDashboardScreen.kt`

- [ ] Add a `PDF 출력` button to completed sessions.
- [ ] Show a placeholder message explaining PDF generation is the next implementation step.

### Task 5: Verification

**Files:**
- Test: all modified code

- [ ] Run `:app:testDebugUnitTest`.
- [ ] Run `:app:assembleDebug`.
- [ ] Commit the implementation.
