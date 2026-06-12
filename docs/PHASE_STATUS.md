# Phase Status

| Phase | Status | Acceptance Tests | Human Checkpoint | Commit |
|---|---|---|---|---|
| Phase 0 - Model decisions + scaffold | Complete | Passed: router-label validation, `mvn verify`, packaged gateway boot | Complete | `phase-0: scaffold project and record model decisions` |
| Phase 1 - Moshi protocol + proxy | Complete | Passed: protocol tests, WebSocket proxy integration tests, reconnect test, `mvn verify` | Complete | `phase-1: document moshi protocol and add ws proxy` |
| Phase 2 - Transcripts + router | Complete | Passed: router-label validation, router eval, heuristic router tests, transcript buffer tests, WebSocket route-decision integration, `mvn verify` | Pending | `phase-2: add transcripts and router` |
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
- [x] Human real-Moshi checkpoint is accepted by request to start Phase 2.

## Phase 2 Acceptance Checklist

- [x] STT sidecar scaffold exists.
- [x] Java `SttClient` interface has stub and real-mode boundary implementations.
- [x] Transcript ring buffer preserves ordering and caps history.
- [x] Router service supports heuristic mode and real LLM mode with fallback.
- [x] ACT route has a canned Phase 2 response path.
- [x] `router.decision` control messages are emitted to the browser.
- [x] Router decisions and utterance events are written to the JSONL event log.
- [x] Offline router eval script runs against `docs/eval/router-labels.jsonl`.
- [x] Heuristic router tests cover the label set and ambiguous cases.
- [x] Transcript buffer tests pass.
- [x] WebSocket integration test covers stub transcript routing.
- [x] `python3 scripts/validate_router_labels.py docs/eval/router-labels.jsonl` passes.
- [x] `python3 scripts/router_eval.py docs/eval/router-labels.jsonl` passes.
- [x] `mvn verify` passes.
- [ ] Human real-LLM router checkpoint is complete.
