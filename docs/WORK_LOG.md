# Work Log

Append-only project history. New entries go at the bottom.

## Entries

- 2026-06-12 12:24 IST - Codex - Phase 0 - Began Phase 0 scaffold after reading `PLAN.md`; initialized git and started creating project-memory files - Files changed: `AGENTS.md`, `PROJECT_CONTEXT.md`, `docs/PROJECT_MEMORY.md`, `docs/WORK_LOG.md`, `docs/PHASE_STATUS.md` - Tests run: pending - Result: in progress - Next step: complete scaffold and run validation.
- 2026-06-12 12:33 IST - Codex - Phase 0 - Completed Phase 0 scaffold, docs, environment defaults, Spring Boot gateway skeleton, CI, compose placeholders, and router labels - Files changed: Phase 0 scaffold files - Tests run: router-label validation, `mvn verify`, packaged gateway boot check - Result: passed - Next step: stop for human checkpoint.
- 2026-06-12 12:43 IST - Codex - Phase 1 - Documented Moshi protocol from Kyutai source; added `/ws/voice`, session lifecycle, Moshi clients, fake Moshi stub, static browser shell, and WebSocket integration tests - Files changed: `docs/moshi-protocol.md`, gateway `ws/session/speech` classes, static web files, fake Moshi files, README, memory files - Tests run: router-label validation, `mvn verify` - Result: passed - Next step: stop for Phase 1 human checkpoint.
- 2026-06-12 12:53 IST - Codex - Phase 2 - Added STT contracts and sidecar scaffold, transcript buffer/service, heuristic and real-router scaffolds, LLM client boundaries, route-decision browser events, JSONL event logging, ACT canned response, offline router eval, browser utterance input, and Phase 2 tests - Files changed: gateway router/transcript/speech/session/ws/static classes, `stt-service`, `scripts/router_eval.py`, README, compose, CI, memory files - Tests run: router-label validation, router eval, `mvn verify` - Result: passed - Next step: stop for Phase 2 human checkpoint and run real-LLM router eval.
