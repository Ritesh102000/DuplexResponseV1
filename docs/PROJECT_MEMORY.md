# Project Memory

## Current Goal
Stop at the Phase 0 human checkpoint. Phase 0 scaffold, docs, validation, and commit are complete.

## Current Phase
Phase 0 - Model interview + scaffolding and design artifacts.

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
- Exact Moshi server protocol and command must be discovered in Phase 1 from pinned source.

## Known Issues
- None yet.

## Next Exact Step
Human reviews Phase 0 output, confirms `docs/decisions.md`, and reviews router label boundaries. After approval, a future agent may start Phase 1.

## Useful Commands
- `mvn verify`
- `python3 scripts/validate_router_labels.py docs/eval/router-labels.jsonl`
- `java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar --server.port=0`
- `git status --short`
