# Phase Status

| Phase | Status | Acceptance Tests | Human Checkpoint | Commit |
|---|---|---|---|---|
| Phase 0 - Model decisions + scaffold | Complete | Passed: router-label validation, `mvn verify`, packaged gateway boot | Pending | `phase-0: scaffold project and record model decisions` |
| Phase 1 - Moshi protocol + proxy | Not started | Not run | Pending | Pending |
| Phase 2 - Transcripts + router | Not started | Not run | Pending | Pending |
| Phase 3 - Ask flow end-to-end | Not started | Not run | Pending | Pending |
| Phase 4 - Suppression + barge-in | Not started | Not run | Pending | Pending |
| Phase 5 - Metrics + evaluation | Not started | Not run | Pending | Pending |
| Phase 6 - Hardening + packaging | Not started | Not run | Pending | Pending |

## Phase 0 Acceptance Checklist

- [x] Project-memory files exist.
- [x] `AGENTS.md` instructs future LLMs to read `PLAN.md` and update memory files.
- [x] `WORK_LOG.md` has an initial entry.
- [x] `PHASE_STATUS.md` reflects Phase 0 state.
- [x] `PROJECT_MEMORY.md` states current phase, completed work, blockers, and next exact step.
- [x] `docs/decisions.md` exists with all 9 interview answers.
- [x] `.env.example` reflects the decisions.
- [x] Gateway boots in stub mode.
- [x] CI runs `mvn verify`.
- [x] Router labels validate with the schema check.
- [x] Commit exists: `phase-0: scaffold project and record model decisions`.
