# Project Context

Current phase: Phase 6 - Hardening + packaging.

Current objective:
Stop at the Phase 6 human checkpoint after adding hardening, packaging, final README sections, and concurrent-session validation.

Current status:
Phase 6 automated stub acceptance is complete and ready for the human checkpoint. `PLAN.md` is the canonical plan. The human request to implement Phase 6 is treated as approval to proceed after Phase 5. Phase 6 added opt-in `/ws/voice` bearer-token auth, browser auto-reconnect, real sidecar startup health retries, transient LLM retry/fallback behavior, pinned Docker images with health checks, CPU/GPU compose packaging, CI image builds, and a three-session isolation integration test. After the Phase 6 commit, real STT was implemented: the gateway now buffers microphone PCM, detects utterance boundaries, sends WAV chunks to `stt-service /transcribe`, and routes returned text through the normal router/backend flow. The STT sidecar now uses `faster-whisper` lazily; the first transcription downloads/loads the model. Docker TTS now uses `espeak-ng` and returns real spoken 24 kHz mono WAV; `stub_sine` is only a fallback if no speech engine is available. For local spoken backend answers, use `STT_MODE=real`, `TTS_MODE=real`, and `LLM_MODE=real`; `TTS_MODE=stub` intentionally plays a tone. A small flow diagnostic helper exists at `scripts/flow_log.py`; it reads `data/events.jsonl` and writes `data/flow-log.md` with per-turn verdicts such as `MOSHI_ONLY`, `BACKEND_SPOKEN`, and missing-injection states.

Recent real-Moshi probe:
Local Moshi MLX exists at `/Users/riteshrajput/.venvs/moshi-mlx/bin/python` with `moshi_mlx 0.3.0`. The server starts with `python -m moshi_mlx.local_web -q 4 --host 127.0.0.1 --port 8998 --no-browser`, loads cached `kyutai/moshiko-mlx-q4`, and accepts `/api/chat` with handshake `00`. `RealMoshiClient` bridges raw 24 kHz PCM from the browser to Ogg/Opus for Moshi and decodes Moshi Ogg/Opus back to raw PCM for the browser. Moshi's `sphn` writer emits OpusHead input rate `48000`, so the Java decoder must decode at 48 kHz and downsample to the browser's 24 kHz PCM. The static UI has AudioWorklet mic capture, queued PCM playback, browser audio unlock, a speaker test, and output peak logging.

Latest local test session:
Moshi and the gateway were started in detached `screen` sessions: `moshi8998` and `gateway8080`. `tts-service` was already listening on `8082`. WebSocket handshake through `ws://127.0.0.1:8080/ws/voice` returned `session.start`; a synthesized speech clip sent through the gateway to real Moshi returned 6 binary PCM frames and Moshi text fragments `Hey` and `what`.

Latest handoff test:
With Moshi still connected in real mode and `LLM_MODE=real`, a typed `transcript.user` for `what is the capital of australia` produced `router.decision` label `ASK` with confidence `0.95`, a harmonized backend answer, `inject.start`, 59 binary PCM frames from real TTS, and `inject.end`.

Latest real-STT test:
The rebuilt STT Docker image installed `faster-whisper==1.2.1`. `GET /health` returned `engine=faster_whisper`, and `POST /transcribe?endTs=12345` with a macOS `say` WAV for "what is the capital of australia" returned `What is the capital of Australia?`. `RealSttClientTests` verifies gateway PCM endpointing posts a WAV to the STT sidecar and receives callback text.

Latest TTS diagnosis:
Logs showed STT `/transcribe` and TTS `/speak` were both being called, but `GET /health` on TTS returned `engine=stub_sine`, so Docker backend injection was a tone. `tts-service` now installs `espeak-ng`; health returns `engine=espeak_ng`, and a `/speak` probe for "Canberra is the capital of Australia." returned a valid 24 kHz, 16-bit, mono WAV.

Next exact step:
Run the Phase 6 human checkpoint: start real Moshi on the Mac, run `docker compose -f docker-compose.yml -f docker-compose.gpu.yml up --build`, exercise a 3-minute demo with real Moshi/STT/backend/TTS, generate a quick handoff log with `python3 scripts/flow_log.py data/events.jsonl --out data/flow-log.md`, generate real metrics with `python3 metrics/analyze.py data/events.jsonl --out metrics/out-real`, and record the final demo video.

Important constraints:
- Do not guess Moshi's wire protocol.
- Stub mode is the default for CI and local development.
- Real Moshi target is local Apple Silicon MLX q4 using `kyutai/moshiko-mlx-q4`.
- Phase 6 CI acceptance uses stub mode, committed fixtures, and Docker image builds; real Moshi, Piper voice quality, hosted LLM behavior, real metrics chart/table generation, and demo video recording remain human checkpoint work.

Validation completed:
- `python3 scripts/validate_router_labels.py docs/eval/router-labels.jsonl`
- `python3 scripts/router_eval.py docs/eval/router-labels.jsonl`
- `mvn verify`
- `mvn -pl gateway -Dtest=OggOpusCodecTests test`
- Phase 1 WebSocket integration tests: byte-equivalent PCM echo and reconnect after stub drop.
- Real Moshi smoke test: started `moshi_mlx.local_web`, ran gateway with `MOSHI_MODE=real`, sent 12 PCM frames through `/ws/voice`, and received a binary PCM response without Moshi Ogg errors.
- Real Moshi spoken-phrase probe before the decoder-rate fix: sent a generated 24 kHz mono phrase through `/ws/voice`; received 26 text/control messages and 256 binary PCM chunks, but Java decoded Moshi `sphn` Opus badly when forced to 24 kHz.
- Moshi `sphn` fixture validation after the decoder-rate fix: a Moshi-style Ogg/Opus fixture decodes to a 3,840-byte browser PCM frame with healthy nonzero signal.
- Real Moshi spoken-phrase probe after the decoder-rate fix: received 26 text/control messages and 216 binary PCM chunks with max decoded peak about 0.5909.
- Phase 2 router and transcript tests: heuristic router label-set coverage, ambiguous routing checks, transcript ordering/capacity, and browser transcript route-decision integration.
- Phase 3 ASK-flow tests: exhaustive session state table, WebSocket ASK event sequence, injected TTS audio, Moshi-audio suppression during injection, stale-drop policy, and supersede policy.
- Phase 3 validation: `mvn -pl gateway -Dtest=SessionStateMachineTests,Phase3AskFlowIntegrationTests test`, `mvn -pl gateway verify`, router-label validation, Python compile checks, JS syntax checks, and `git diff --check`.
- Local TTS probe: macOS `say` + `afconvert` produced 24 kHz, 16-bit mono WAV for backend answer speech.
- TTS service setup probe: default `python3` is 3.14 and failed pinned Pydantic/PyO3 install; `/opt/homebrew/bin/python3.12` venv installed successfully and `/speak` returned valid WAV.
- Real-TTS gateway smoke test: gateway on port 18080 with `TTS_MODE=real`, `LLM_MODE=stub`, TTS sidecar on 8082; WebSocket ASK received `inject.start`, 24 binary PCM frames, and `inject.end`.
- Real-TTS buffer fix: `RealTtsClient` now consumes the WAV response as `DataBuffer` chunks so macOS `say` responses larger than Spring WebClient's default 256 KB memory limit do not fail.
- Real-Moshi gateway smoke test after the TTS buffer fix: detached `screen` sessions kept Moshi on 8998 and gateway on 8080 alive; WebSocket handshake returned `session.start`, and synthesized speech sent through `/ws/voice` produced Moshi text plus binary PCM response frames.
- Real backend handoff smoke test: typed ASK through `/ws/voice` while gateway was in `MOSHI_MODE=real`, `LLM_MODE=real`, and `TTS_MODE=real`; observed `router.decision`, backend answer, `inject.start`, 59 audio frames, and `inject.end`.
- Phase 4 focused validation: `mvn -pl gateway -Dtest=SessionStateMachineTests,Phase4SuppressionBargeInIntegrationTests test`.
- Phase 4 full validation: `mvn -pl gateway verify`, router-label validation, router eval, Python compile checks including fake Moshi, JS syntax checks, and `git diff --check`.
- Phase 5 fixture analysis: `python3 metrics/analyze.py metrics/fixtures/events.jsonl --out metrics/out`.
- Phase 5 stub flow judge: `python3 metrics/judge_flow.py metrics/fixtures/judge-samples.jsonl --out metrics/out --mode stub`.
- Phase 5 full gateway validation: `mvn -pl gateway verify`.
- Phase 6 focused validation: `mvn -pl gateway -Dtest=BearerTokenHandshakeIntegrationTests,ConcurrentSessionIsolationIntegrationTests test`.
- Phase 6 compose validation: `docker compose config` and `docker compose -f docker-compose.yml -f docker-compose.gpu.yml config`.
- Phase 6 browser validation: `node --check gateway/src/main/resources/static/app.js`.
- Phase 6 full validation: `mvn -pl gateway verify`, router/metrics scripts, Python compile checks, JS syntax checks, Docker image build, CPU compose stack healthy, and endpoint probes for gateway/STT/TTS/Prometheus/Grafana.
- Flow-log helper validation: `python3 -m py_compile scripts/flow_log.py`, `python3 scripts/flow_log.py data/events.jsonl --out data/flow-log.md --tail 12`, and `python3 scripts/flow_log.py metrics/fixtures/events.jsonl --out metrics/out/flow-log.md`.
- Real-STT validation: `mvn -pl gateway -Dtest=RealSttClientTests test`, `mvn -pl gateway verify`, `python3 -m py_compile stt-service/app/main.py ...`, `docker compose build stt`, `docker compose build gateway`, `curl http://localhost:8081/health`, and a real `/transcribe` call with generated speech audio.
- Real Docker TTS validation: `python3 -m py_compile tts-service/app/main.py`, `docker compose -f docker-compose.yml -f docker-compose.gpu.yml build tts`, recreated TTS, `curl http://localhost:8082/health` returned `engine=espeak_ng`, and `POST /speak` returned a 24 kHz mono WAV.
