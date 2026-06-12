# Project Memory

## Current Goal
Stop at the Phase 2 human checkpoint. Phase 2 STT boundaries, transcript memory, router classification, ACT canned response path, route-decision events, event logging, and offline router evaluation are complete in stub mode.

## Current Phase
Phase 2 - Transcripts + router.

## Completed Work
- Renamed the restart packet to `PLAN.md`.
- Added project-memory and LLM handoff rules to `PLAN.md`.
- Initialized the repository with git.
- Created project-memory files and initial handoff context.
- Recorded model decisions in `docs/decisions.md`.
- Generated `.env.example` from the recorded decisions.
- Added a bootable Spring Boot 3.x Java 21 gateway skeleton.
- Added sidecar/stub placeholder directories.
- Added architecture docs, CI workflow, compose files, and router-label validation.
- Added 140 router-label examples: 50 CHAT, 50 ASK, 40 ACT, with 26 ambiguous examples.
- Ran Phase 0 validation successfully.
- Human requested Phase 1, which is treated as Phase 0 checkpoint approval.
- Extracted Moshi WebSocket protocol from Kyutai source into `docs/moshi-protocol.md`.
- Added Moshi protocol encoder/decoder helpers and message types.
- Added `MoshiClient`, `StubMoshiClient`, and `RealMoshiClient` Spring wiring.
- Added `/ws/voice` browser WebSocket handler and IDLE/LISTENING session state.
- Added minimal static browser page served by the gateway.
- Added protocol-compatible Python fake Moshi server under `stubs/fake-moshi/`.
- Added Phase 1 integration tests for byte-equivalent PCM pass-through and reconnect after stub drop.
- Human requested Phase 2, which is treated as Phase 1 checkpoint approval.
- Added transcript domain objects and a bounded per-session transcript buffer.
- Added STT client contracts with stub and real-mode boundary implementations.
- Added a FastAPI STT sidecar scaffold with health and placeholder transcription endpoints.
- Updated the browser gateway flow so stub text utterances can enter through `/ws/voice`.
- Added router domain objects, heuristic routing, real LLM routing scaffold, and strict fallback behavior.
- Added LLM client contracts with stub and OpenAI-compatible implementations.
- Added `ConversationCoordinator` to store user/Moshi transcript lines, classify user utterances, emit route decisions, and send the Phase 2 canned ACT response.
- Added JSONL event logging for utterance and router-decision events.
- Added offline router eval script and wired it into CI.
- Added router/transcript tests and extended WebSocket integration coverage for text utterance routing.
- Updated README, compose, static browser controls, and STT sidecar docs for Phase 2.

## Important Architecture
- Gateway is Spring Boot 3.x, Java 21, Maven.
- Browser and Moshi audio use raw binary WebSockets in later phases.
- External systems must sit behind Java interfaces with stub and real implementations.
- Stub mode must pass without GPU, API key, or model-provider network access.
- Phase 2 route decisions are emitted to the browser as `router.decision` control messages and logged to `./data/events.jsonl`.
- The browser can send stub utterances as `{"type":"transcript.user","text":"..."}` over `/ws/voice` for local Phase 2 testing.

## Known Decisions
- Hardware: Apple Mac M4 with 16 GB unified memory.
- Moshi runtime: local Apple Silicon MLX q4, `kyutai/moshiko-mlx-q4`.
- Voice: `moshiko` male.
- LLM: hosted OpenAI-compatible API using `gpt-5.4-mini`.
- STT: faster-whisper small on CPU with Silero VAD.
- TTS: Piper CPU with a natural male English voice.
- API budget: 20 USD/month.
- Demo topology: Mac + hosted API.

## Open Questions
- Exact Piper voice can be chosen later when the TTS service is implemented.
- Real hosted LLM router eval has not been run yet.

## Known Issues
- `MOSHI_MODE=real` has the documented Moshi message envelope in place, but real Moshi audio quality/latency still requires the Phase 1 human checkpoint with local Moshi.
- `STT_MODE=real` is scaffolded but real streaming transcription is not implemented beyond sidecar boundaries.
- `ROUTER_MODE=real` has an OpenAI-compatible client and fallback path, but it has not been exercised with a real API key/model in this phase.
- Phase 2 ACT replies are canned text control messages; TTS audio for replies starts in Phase 3.

## Next Exact Step
Human runs the Phase 2 real-LLM router checkpoint, records the eval accuracy/confusion matrix, and approves the phase. After approval, a future agent may start Phase 3.

## Useful Commands
- `mvn verify`
- `python3 scripts/validate_router_labels.py docs/eval/router-labels.jsonl`
- `python3 scripts/router_eval.py docs/eval/router-labels.jsonl`
- `java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar --server.port=0`
- `python3 stubs/fake-moshi/fake_moshi.py --port 8998`
- `uvicorn stt-service.app.main:app --host 0.0.0.0 --port 8002`
- `git status --short`
