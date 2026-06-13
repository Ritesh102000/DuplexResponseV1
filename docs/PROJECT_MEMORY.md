# Project Memory

## Current Goal
Stop at the Phase 6 human checkpoint. Phase 6 hardening and packaging are complete in automated stub mode: `/ws/voice` has opt-in bearer-token auth, browser auto-reconnect exists, real sidecars have startup health retries, real LLM calls retry/fallback on transient failures, Docker images are pinned/health-checked, compose packaging is updated, CI builds local images, and concurrent-session isolation is tested.

## Current Phase
Phase 6 - Hardening + packaging.

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
- Re-tested the local real-Moshi path after the TTS fixes using detached `screen` sessions for Moshi and the gateway; `/ws/voice` returned `session.start`, and a synthesized speech clip sent through the gateway produced Moshi text fragments plus 6 binary PCM response frames.
- Verified the backend handoff path with real Moshi connected and real backend LLM enabled: a typed ASK utterance routed to `ASK`, the backend LLM answered, real TTS streamed 59 PCM frames, and `inject.end` arrived.
- Human requested Phase 4, which is treated as Phase 3 checkpoint approval.
- Added `SuppressionGate`: while a session is `ASK_IN_FLIGHT`, Moshi text is token-counted; short acknowledgments pass, but long Moshi answer attempts trigger `suppression.faded` and audio fades to zero over the configured fade window.
- Added `BargeInDetector`: while a session is `INJECTING`, sustained user PCM energy over the configured threshold/duration emits `barge_in`, cancels the active TTS injection, and returns the state machine to `LISTENING`.
- Made `OutboundMixer` TTS injections cancellable and exposed active injection correlation IDs for barge-in logging.
- Added stub Moshi text fixture frames for Java integration tests and `ack` / `long-answer` fixture modes to `stubs/fake-moshi/fake_moshi.py`.
- Added Phase 4 integration tests for long-answer suppression energy, acknowledgment pass-through, and barge-in cancellation within 500 ms.
- Ran Phase 4 validation successfully.
- Human requested Phase 5, which is treated as approval to proceed after Phase 4; a formal 20-question suppression-rate dataset was not separately recorded.
- Added Micrometer event mirroring through `MetricsFacade`; JSONL events now increment `voice.events` counters and record `voice.event.latency` timers when events contain `latencyMs`.
- Exposed the gateway Prometheus endpoint at `/actuator/prometheus`.
- Added `MoshiFirstAudioTracker` to log `moshi.first_audio` for ASK turns when Moshi starts speaking before backend injection.
- Added Prometheus and Grafana provisioning under `ops/`, including the `Voice Two Brains` dashboard.
- Updated `docker-compose.yml` with Prometheus, Grafana, and a mounted gateway event-log data directory.
- Added `metrics/analyze.py` to compute perceived-vs-true ASK latency, suppression rate, barge-in count, stale-drop count, router confusion matrix, and a PNG latency chart from JSONL logs.
- Added committed metrics fixtures under `metrics/fixtures/`.
- Added `metrics/judge_flow.py` with stub and OpenAI-compatible real judge modes for natural spoken continuation / flow-break scoring.
- Wired Phase 5 analysis and stub judge commands into CI.
- Ran Phase 5 validation successfully.
- Human requested Phase 6, which is treated as approval to proceed after Phase 5; the real-runtime metrics chart/table remains a human checkpoint artifact.
- Added `BearerTokenHandshakeInterceptor`; `/ws/voice` accepts all clients by default, but when `VOICE_WS_TOKEN` is set it requires `Authorization: Bearer ...` or `?token=...`.
- Updated the browser client to pass auth tokens from `?token=` or `localStorage.voiceWsToken` and auto-reconnect after non-manual socket drops.
- Added `SidecarHealthVerifier` so real STT/TTS modes retry `/health` at startup and fail fast if sidecars are unavailable.
- Added retry handling to `OpenAiCompatibleLlmClient` for transient failures and LLM fallback behavior in `AskJobService`.
- Updated Dockerfiles with pinned base image tags and container health checks.
- Added `.dockerignore` files for gateway, STT, and TTS build contexts.
- Updated CPU compose health checks and `docker-compose.gpu.yml` for Mac-hosted MLX Moshi, optional Linux Moshi, and optional Ollama.
- Wired Docker compose config validation and local image builds into CI.
- Added Phase 6 tests for bearer-token WebSocket auth and three concurrent session isolation.
- Updated README and architecture docs with final packaging, hard-problems, metrics, and future-scope sections.
- Ran Phase 6 validation successfully, including Docker image build and CPU compose stack health checks.
- Added `scripts/flow_log.py`, a compact diagnostic helper that reads gateway JSONL events and writes `data/flow-log.md` with per-turn flow verdicts (`MOSHI_ONLY`, `BACKEND_SPOKEN`, and partial backend states), then wired the fixture flow-log command into CI.
- Implemented real microphone STT after the Phase 6 checkpoint feedback: `RealSttClient` now buffers browser PCM, detects utterance boundaries, posts WAV chunks to `stt-service /transcribe`, and forwards returned text into the existing router/backend path. `stt-service` now uses lazy `faster-whisper` transcription instead of returning an empty placeholder response.

## Important Architecture
- Gateway is Spring Boot 3.x, Java 21, Maven.
- Browser and Moshi audio use raw binary WebSockets in later phases.
- External systems must sit behind Java interfaces with stub and real implementations.
- Stub mode must pass without GPU, API key, or model-provider network access.
- Phase 2 route decisions are emitted to the browser as `router.decision` control messages and logged to `./data/events.jsonl`.
- The browser can send stub utterances as `{"type":"transcript.user","text":"..."}` over `/ws/voice` for local Phase 2 testing.
- Phase 3 ASK flow emits `utterance.end`, `router.decision`, `job.dispatched`, `job.completed`, `job.dropped_stale`, `inject.start`, and `inject.end` JSONL events.
- Phase 4 adds `suppression.faded` and `barge_in` JSONL events.
- Phase 5 adds `moshi.first_audio` JSONL events for ASK perceived-latency measurement.
- `scripts/flow_log.py` is the quick handoff-debug tool for local runs; run it after a browser test to see whether the turn reached `router.decision`, backend job completion, and `inject.start/end`.
- `STT_MODE=real` is now functional. Java endpointing uses `STT_ENERGY_THRESHOLD`, `STT_MIN_SPEECH_MS`, `STT_SILENCE_MS`, and `STT_MAX_UTTERANCE_MS`; Python transcription uses `STT_WHISPER_MODEL`, `STT_DEVICE`, `STT_COMPUTE_TYPE`, and `STT_LANGUAGE`.
- Every JSONL event is mirrored to Micrometer counter `voice.events`; events with `latencyMs` are mirrored to timer `voice.event.latency`.
- Prometheus scrapes gateway metrics from `/actuator/prometheus`; Grafana provisioning and the `Voice Two Brains` dashboard live under `ops/grafana/`.
- `metrics/analyze.py` is standard-library Python and writes `latency_chart.png`, `summary.json`, `ask_latencies.csv`, and `router_confusion_matrix.csv`.
- `metrics/judge_flow.py` runs in stub mode for CI and can call an OpenAI-compatible `/chat/completions` endpoint in real mode when `LLM_API_KEY` is set.
- `/ws/voice` bearer-token auth is opt-in. Blank `VOICE_WS_TOKEN` preserves local dev behavior; a set token requires `Authorization: Bearer <token>` or `?token=<token>`.
- The static browser client reconnects automatically up to 5 times after a socket close unless the user clicked Disconnect.
- In real sidecar modes, `SidecarHealthVerifier` checks `STT_URL/health` and `TTS_URL/health` at startup with configured retries.
- Real LLM calls retry retryable 429/5xx/transport failures and ASK jobs inject a spoken fallback if answer/harmonizer calls fail.
- `AskJobService` owns in-process virtual-thread ASK jobs and one active ASK per session; a newer ASK supersedes the previous one.
- `OutboundMixer` is now the only gateway component that writes binary audio to the browser.
- `SuppressionGate` sits between Moshi callbacks and `OutboundMixer`; it watches Moshi text during `ASK_IN_FLIGHT` and fades long-answer audio to silence.
- `BargeInDetector` observes inbound browser PCM during `INJECTING`; if user speech lasts at least `BARGE_IN_MIN_MS`, it cancels the active injection.
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
- Phase 5 real judge has not been run against a real conversation sample set yet.
- The optional Linux Moshi compose service defaults to `MOSHI_PIP_PACKAGE=moshi==0.2.10`; confirm the exact package/module before relying on that path for a GPU host.

## Known Issues
- `MOSHI_MODE=real` now carries audio through the Java PCM/Ogg-Opus bridge and browser mic capture exists. Automated spoken-phrase probes return Moshi text and decoded PCM. Conversational quality and latency still need a human mic/speaker checkpoint with local Moshi.
- The GPU compose overlay now defaults to `STT_MODE=real`. The first spoken turn can be slow while faster-whisper downloads/loads the configured model; subsequent turns should be faster.
- If the human hears Moshi answering but no backend answer, generate `data/flow-log.md`; no new `utterance.end` / `router.decision` entries usually means STT did not produce text or the gateway is not actually running with `STT_MODE=real`.
- `TTS_MODE=real` has a gateway-side client and FastAPI sidecar contract. On macOS it can speak through `say` + `afconvert`; on systems without those tools it falls back to deterministic tone audio until a concrete Piper voice/model path is installed.
- `OutboundMixer` paces injected PCM frames at 80 ms/frame. If the browser shows `inject.start` without `inject.end`, restart the gateway to make sure the patched jar is running.
- If the browser shows `TTS_STREAM_FAILED` with `Exceeded limit on max bytes to buffer : 262144`, the running gateway is stale; rebuild/restart with the commit that streams `DataBuffer` chunks in `RealTtsClient`.
- FastAPI sidecar virtualenvs should be created with `/opt/homebrew/bin/python3.12` on this Mac. The default `python3` is 3.14 and can fail against pinned Pydantic/PyO3 wheels.
- `LLM_MODE=real` has been smoke-tested for one backend handoff, but full real-router evaluation has not been run yet.
- Phase 4 was advanced by user request to start Phase 5, but the formal 20-question real-runtime suppression-rate dataset was not recorded as a separate artifact.
- Existing `data/events.jsonl` predates Phase 5 `moshi.first_audio` instrumentation, so it cannot produce perceived-latency rows until the patched gateway records fresh ASK turns.
- The Phase 5 human checkpoint still needs a fresh real-runtime metrics run and README chart/table update.
- The Phase 6 human checkpoint still needs a full real-runtime compose run and a recorded 3-minute demo video.

## Next Exact Step
Human runs the Phase 6 checkpoint: start real Moshi on the Mac, run `docker compose -f docker-compose.yml -f docker-compose.gpu.yml up --build`, exercise the full real Moshi/STT/backend/TTS demo, run `python3 scripts/flow_log.py data/events.jsonl --out data/flow-log.md`, run `python3 metrics/analyze.py data/events.jsonl --out metrics/out-real`, paste the real chart/table into `README.md`, and record the 3-minute demo video.

## Useful Commands
- `mvn -pl gateway verify`
- `mvn -pl gateway -Dtest=SessionStateMachineTests,Phase3AskFlowIntegrationTests test`
- `mvn -pl gateway -Dtest=SessionStateMachineTests,Phase4SuppressionBargeInIntegrationTests test`
- `mvn -pl gateway -Dtest=OggOpusCodecTests test`
- `python3 scripts/validate_router_labels.py docs/eval/router-labels.jsonl`
- `python3 scripts/router_eval.py docs/eval/router-labels.jsonl`
- `python3 metrics/analyze.py metrics/fixtures/events.jsonl --out metrics/out`
- `python3 scripts/flow_log.py data/events.jsonl --out data/flow-log.md`
- `python3 scripts/flow_log.py metrics/fixtures/events.jsonl --out metrics/out/flow-log.md`
- `python3 metrics/judge_flow.py metrics/fixtures/judge-samples.jsonl --out metrics/out --mode stub`
- `python3 metrics/analyze.py data/events.jsonl --out metrics/out-real`
- `python3 -m py_compile stt-service/app/main.py tts-service/app/main.py scripts/router_eval.py scripts/validate_router_labels.py stubs/fake-moshi/fake_moshi.py metrics/analyze.py metrics/judge_flow.py`
- `node --check gateway/src/main/resources/static/app.js && node --check gateway/src/main/resources/static/mic-capture-worklet.js`
- `mvn -pl gateway -Dtest=BearerTokenHandshakeIntegrationTests,ConcurrentSessionIsolationIntegrationTests test`
- `mvn -pl gateway -Dtest=RealSttClientTests test`
- `docker compose config`
- `docker compose -f docker-compose.yml -f docker-compose.gpu.yml config`
- `docker compose build gateway stt tts`
- `docker compose up --build gateway prometheus grafana`
- `docker compose -f docker-compose.yml -f docker-compose.gpu.yml up --build`
- `java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar --server.port=0`
- `python3 stubs/fake-moshi/fake_moshi.py --port 8998`
- `python3 stubs/fake-moshi/fake_moshi.py --port 8998 --fixture ack`
- `python3 stubs/fake-moshi/fake_moshi.py --port 8998 --fixture long-answer`
- `/Users/riteshrajput/.venvs/moshi-mlx/bin/python -m moshi_mlx.local_web -q 4 --host 127.0.0.1 --port 8998 --no-browser`
- `MOSHI_MODE=real MOSHI_WS_URL=ws://127.0.0.1:8998/api/chat STT_MODE=real STT_URL=http://127.0.0.1:8081 LLM_MODE=stub TTS_MODE=stub java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar`
- `screen -ls`
- `screen -r moshi8998`
- `screen -r gateway8080`
- `cd stt-service && /opt/homebrew/bin/python3.12 -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt && uvicorn app.main:app --host 0.0.0.0 --port 8081`
- `cd tts-service && /opt/homebrew/bin/python3.12 -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt && uvicorn app.main:app --host 0.0.0.0 --port 8082`
- `git status --short`
