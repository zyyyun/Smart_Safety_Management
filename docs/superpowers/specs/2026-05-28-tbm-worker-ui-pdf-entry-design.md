# TBM Worker UI and PDF Entry Design

## Goal
Improve the worker TBM experience using layout option A, remove manual manager work-scope naming, and add a visible PDF export entry point for completed TBM sessions.

## Scope
- Manager TBM start no longer asks for a work-scope name.
- The app derives the session display name from selected OPS titles, joined with ` + `.
- Worker TBM screen is fully scrollable with a bottom action area containing signature controls and submit action.
- Worker TBM details show multi-OPS hazards, controls, and checklist items grouped by OPS title.
- Font sizes on the worker TBM screen increase slightly for readability.
- Completed manager TBM cards expose a `PDF 출력` button UI. Actual PDF file generation is intentionally out of scope for this pass.

## UI Rules
- Worker screen title remains at the top of the scrollable content.
- Session summary appears before detailed content.
- Hazards, controls, and checklist items are grouped by OPS title. Items without metadata appear under `기타`.
- The bottom submit area remains reachable even when the checklist is long.
- Completed-session PDF button must not falsely claim a PDF was generated; it shows a clear “준비 중” message until generation is implemented.

## Data Flow
- Manager selected templates -> `selectedOpsSessionTitle()` -> `TbmStartRequest.workScope`.
- Existing `hazards_snapshot`, `controls_snapshot`, and prefixed checklist rows power worker grouping.
- Existing check-in endpoint continues to handle signature upload and participation.
