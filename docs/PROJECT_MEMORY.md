# Project Memory

## Current Goal
Stop at the Phase 1 human checkpoint. Phase 1 protocol documentation, gateway WebSocket proxy surface, fake Moshi stub, browser shell, and stub-mode tests are complete.

## Current Phase
Phase 1 - Moshi protocol doc + audio pass-through proxy.

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

## Important Architecture
- Gateway is Spring Boot 3.x, Java 21, Maven.
- Browser and Moshi audio use raw binary WebSockets in later phases.
- External systems must sit behind Java interfaces with stub and real implementations.
- Stub mode must pass without GPU, API key, or model-provider network access.

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
- Real Moshi checkpoint has not been run yet.

## Known Issues
- `MOSHI_MODE=real` has the documented Moshi message envelope in place, but real Moshi audio quality/latency still requires the Phase 1 human checkpoint with local Moshi.

## Next Exact Step
Human starts real Moshi and confirms a full-duplex conversation through the gateway. After approval, a future agent may start Phase 2.

## Useful Commands
- `mvn verify`
- `python3 scripts/validate_router_labels.py docs/eval/router-labels.jsonl`
- `java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar --server.port=0`
- `python3 stubs/fake-moshi/fake_moshi.py --port 8998`
- `git status --short`
