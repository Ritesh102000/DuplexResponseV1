# Project Memory

## Current Goal
Stop at the Phase 3 human checkpoint. Phase 3 ASK flow is complete in stub mode: delayed backend answer jobs, correlation IDs, per-session supersede, stale-drop policy, harmonizer, TTS injection, outbound mixer suppression, state-machine tests, and WebSocket integration tests.

## Current Phase
Phase 3 - Ask flow end-to-end.

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
- Probed local Moshi MLX installation and confirmed `/api/chat` handshake works.
- Added a pure-Java PCM to Ogg/Opus bridge for `RealMoshiClient` using Concentus plus local Ogg page parsing/writing.
- Updated `RealMoshiClient` so real mode sends Ogg/Opus payloads to Moshi and decodes Moshi Ogg/Opus audio back to raw browser PCM.
- Added AudioWorklet microphone capture to the static browser UI with 24 kHz/80 ms PCM packetization.
- Added Ogg/Opus codec unit tests and fixed WebSocket send/close race handling.
- Ran a local real-Moshi gateway smoke test through `/ws/voice` and received decoded PCM from real Moshi.
- Improved browser response playback with audio-context unlock, sequential PCM scheduling, a local speaker test, and debug peak logging.
- Fixed the actual Moshi response-audio distortion by decoding Moshi `sphn` Opus at 48 kHz, then downsampling to the browser's 24 kHz PCM; reduced browser output gain back to unity and added a Moshi-style `sphn` Opus fixture test.
- Re-ran a clean local real-Moshi spoken-phrase probe after the decoder-rate fix; the patched gateway returned 26 text/control messages, 216 binary PCM chunks, and max decoded peak about 0.5909.
- Human requested Phase 3, which is treated as Phase 2 checkpoint approval.
- Added the Phase 3 ASK job flow with `AskJobService`, correlation IDs, virtual-thread execution, timeout handling, per-session supersede, stale-drop/reintroduce policy, and JSONL job events.
- Added answer and harmonizer prompts plus `AnswerService`, `Harmonizer`, and `LlmHarmonizer`; stub LLM now returns a deterministic delayed answer and harmonized spoken response.
- Added `TtsClient` with stub and real sidecar implementations, PCM frame helpers, and a FastAPI `tts-service` scaffold exposing `GET /health` and `POST /speak`.
- Added `OutboundMixer` as the single outbound audio writer; Moshi audio is suppressed while TTS injection is active.
- Expanded session states to `IDLE`, `LISTENING`, `MOSHI_TALKING`, `ASK_IN_FLIGHT`, and `INJECTING`, with a Phase 3 state-machine implementation.
- Updated browser control messages to include ASK `correlationId`, `inject.start`, and `inject.end`.
- Added Phase 3 tests for state transitions, exact ASK event sequence, injected TTS audio, Moshi suppression during injection, stale drop, and supersede.
- Ran Phase 3 validation successfully.
- Updated `tts-service` after browser testing so macOS local runs use `say` + `afconvert` for spoken WAV output; the deterministic tone remains the fallback for stub/CI environments.
- Rebuilt the TTS virtualenv with `/opt/homebrew/bin/python3.12` after Python 3.14 failed to install pinned `pydantic-core`; local sidecar docs now use Python 3.12 explicitly.
- Fixed real-TTS injection completion by making `RealTtsClient` fetch audio inside the reactive stream and pacing outbound TTS PCM frames in `OutboundMixer`; verified WebSocket real-TTS smoke receives 24 binary frames and `inject.end`.
- Fixed real-TTS WAV buffer handling by reading the TTS sidecar response as `DataBuffer` chunks instead of `byte[]`, avoiding Spring WebClient's default 256 KB in-memory limit for macOS `say` WAV responses.

## Important Architecture
- Gateway is Spring Boot 3.x, Java 21, Maven.
- Browser and Moshi audio use raw binary WebSockets in later phases.
- External systems must sit behind Java interfaces with stub and real implementations.
- Stub mode must pass without GPU, API key, or model-provider network access.
- Phase 2 route decisions are emitted to the browser as `router.decision` control messages and logged to `./data/events.jsonl`.
- The browser can send stub utterances as `{"type":"transcript.user","text":"..."}` over `/ws/voice` for local Phase 2 testing.
- Phase 3 ASK flow emits `utterance.end`, `router.decision`, `job.dispatched`, `job.completed`, `job.dropped_stale`, `inject.start`, and `inject.end` JSONL events.
- `AskJobService` owns in-process virtual-thread ASK jobs and one active ASK per session; a newer ASK supersedes the previous one.
- `OutboundMixer` is now the only gateway component that writes binary audio to the browser.
- `TtsClient` returns raw 24 kHz, 16-bit mono PCM frames to match the browser WebSocket contract.
- Local real Moshi is installed at `/Users/riteshrajput/.venvs/moshi-mlx/bin/python` and can be started with `python -m moshi_mlx.local_web -q 4 --host 127.0.0.1 --port 8998 --no-browser`.
- In `MOSHI_MODE=real`, the gateway now keeps the browser contract as raw 24 kHz PCM and bridges to Moshi's Ogg/Opus WebSocket payloads internally.
- Moshi `sphn` response streams use an OpusHead input rate of 48 kHz. `OggOpusDecoder` must decode at 48 kHz and downsample to 24 kHz; decoding those packets directly at 24 kHz produces corrupted/near-silent audio.
- The browser UI can talk to real Moshi by clicking `Test Speaker`, then `Connect`, then `Start Mic`.
- Browser debug audio lines show raw decoded Moshi `peak` and output `out` levels; chunks near `0.000` are silence from Moshi, not a transport failure.

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
- Real hosted LLM answer/harmonizer behavior has not been run yet.

## Known Issues
- `MOSHI_MODE=real` now carries audio through the Java PCM/Ogg-Opus bridge and browser mic capture exists. A spoken-phrase probe returned Moshi text and decoded PCM, but conversational quality and latency still need a human mic/speaker checkpoint with local Moshi.
- `STT_MODE=real` is scaffolded but real streaming transcription is not implemented beyond sidecar boundaries.
- `TTS_MODE=real` has a gateway-side client and FastAPI sidecar contract. On macOS it can speak through `say` + `afconvert`; on systems without those tools it falls back to deterministic tone audio until a concrete Piper voice/model path is installed.
- `OutboundMixer` paces injected PCM frames at 80 ms/frame. If the browser shows `inject.start` without `inject.end`, restart the gateway to make sure the patched jar is running.
- If the browser shows `TTS_STREAM_FAILED` with `Exceeded limit on max bytes to buffer : 262144`, the running gateway is stale; rebuild/restart with the commit that streams `DataBuffer` chunks in `RealTtsClient`.
- FastAPI sidecar virtualenvs should be created with `/opt/homebrew/bin/python3.12` on this Mac. The default `python3` is 3.14 and can fail against pinned Pydantic/PyO3 wheels.
- `ROUTER_MODE=real` has an OpenAI-compatible client and fallback path, but it has not been exercised with a real API key/model in this phase.
- Phase 3 injection suppresses Moshi audio during injected TTS. Smooth fade/duck polish and barge-in cancellation are Phase 4 work.

## Next Exact Step
Human runs the Phase 3 checkpoint with real Moshi plus the gateway: ask a factual question, verify Moshi acknowledges within about 1s, and verify the injected harmonized answer is spoken after the backend delay. After approval, a future agent may start Phase 4.

## Useful Commands
- `mvn -pl gateway verify`
- `mvn -pl gateway -Dtest=SessionStateMachineTests,Phase3AskFlowIntegrationTests test`
- `mvn -pl gateway -Dtest=OggOpusCodecTests test`
- `python3 scripts/validate_router_labels.py docs/eval/router-labels.jsonl`
- `python3 scripts/router_eval.py docs/eval/router-labels.jsonl`
- `python3 -m py_compile stt-service/app/main.py tts-service/app/main.py scripts/router_eval.py scripts/validate_router_labels.py`
- `node --check gateway/src/main/resources/static/app.js && node --check gateway/src/main/resources/static/mic-capture-worklet.js`
- `java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar --server.port=0`
- `python3 stubs/fake-moshi/fake_moshi.py --port 8998`
- `/Users/riteshrajput/.venvs/moshi-mlx/bin/python -m moshi_mlx.local_web -q 4 --host 127.0.0.1 --port 8998 --no-browser`
- `MOSHI_MODE=real MOSHI_WS_URL=ws://127.0.0.1:8998/api/chat STT_MODE=stub LLM_MODE=stub TTS_MODE=stub java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar`
- `cd stt-service && /opt/homebrew/bin/python3.12 -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt && uvicorn app.main:app --host 0.0.0.0 --port 8081`
- `cd tts-service && /opt/homebrew/bin/python3.12 -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt && uvicorn app.main:app --host 0.0.0.0 --port 8082`
- `git status --short`
