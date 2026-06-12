# Phase Status

| Phase | Status | Acceptance Tests | Human Checkpoint | Commit |
|---|---|---|---|---|
| Phase 0 - Model decisions + scaffold | Complete | Passed: router-label validation, `mvn verify`, packaged gateway boot | Complete | `phase-0: scaffold project and record model decisions` |
| Phase 1 - Moshi protocol + proxy | Complete | Passed: protocol tests, WebSocket proxy integration tests, reconnect test, `mvn verify` | Pending | `phase-1: document moshi protocol and add ws proxy` |
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

## Phase 1 Acceptance Checklist

- [x] `docs/moshi-protocol.md` exists and is based on Kyutai source.
- [x] Fake Moshi stub exists under `stubs/fake-moshi/`.
- [x] `RealMoshiClient` and `StubMoshiClient` are wired by `voice.moshi-mode`.
- [x] Browser WebSocket endpoint `/ws/voice` exists.
- [x] Minimal browser client exists under gateway static resources.
- [x] Session lifecycle covers IDLE to LISTENING for Phase 1.
- [x] Integration test asserts byte-equivalent PCM pass-through in stub mode.
- [x] Reconnect test asserts session reset after stub drop.
- [x] `mvn verify` passes.
- [ ] Human real-Moshi checkpoint is complete.
