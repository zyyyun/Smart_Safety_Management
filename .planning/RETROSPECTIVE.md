# Smart Safety Management - Retrospective

## Milestone: v1.1 앱 전체 완성도

**Shipped:** 2026-06-04  
**Close type:** scope-reduced  
**Audit:** `.planning/milestones/v1.1-MILESTONE-AUDIT.md`

### What Was Built

- Shared UI language through common tokens/components and Setting scaffold work.
- TBM flow stabilization: multi OPS, immediate refresh after session actions, worker submit-to-home, and Compose crash fixes.
- Daily checklist selected-date persistence fix.
- AI event detail/list capture mapping to stored Supabase capture URLs.
- Visible RTSP YOLO startup path through PowerShell/task scheduler scripts.
- J2208A watch runtime stabilization with B-mode read loop, runtime state, stale callback guards, and pairing telemetry suppression.

### What Worked

- Fast feedback from device screenshots/logs exposed issues that static planning missed.
- Small commits kept late-stage fixes understandable.
- The audit made a useful distinction between code evidence and formal GSD closure evidence.

### What Was Inefficient

- GSD phase artifacts fell behind real code changes.
- Docker remained in ROADMAP after the practical path shifted to a visible PowerShell scheduled task.
- Verification artifacts were not created as fixes landed, so milestone closure required a scope-reduced decision.

### Patterns Established

- Treat device/runtime state as reactive UI data, not a one-time DB snapshot.
- For hardware startup scripts, visible operator feedback is often more valuable than hidden Windows services.
- Keep operational pivots reflected in ROADMAP immediately.

### Key Lessons

- If a requirement changes direction, amend the milestone before continuing implementation.
- Quick fixes that satisfy real user pain should still produce a small verification artifact.
- The next milestone should start with UAT and operation-model decisions before adding more features.

## Cross-Milestone Trends

| Trend | Evidence | Next Action |
|---|---|---|
| Hardware integration needs live verification | Watch/RTSP/TBM issues surfaced on device | Make UAT a first-class phase |
| Planning drift accumulates quickly | v1.1 code changed faster than `.planning` artifacts | Update planning after each accepted pivot |
| Operator visibility matters | NSSM was hard to observe; visible PowerShell preferred | Decide Docker vs scheduled task explicitly |
