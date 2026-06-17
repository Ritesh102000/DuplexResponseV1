# Project Context

Current phase: Phase 6 - Hardening + packaging.

Current objective:
Stop at the Phase 6 human checkpoint after adding hardening, packaging, final README sections, and concurrent-session validation.

Current status:
Phase 6 automated stub acceptance is complete and ready for the human checkpoint. `PLAN.md` is the canonical plan. The human request to implement Phase 6 is treated as approval to proceed after Phase 5. Phase 6 added opt-in `/ws/voice` bearer-token auth, browser auto-reconnect, real sidecar startup health retries, transient LLM retry/fallback behavior, pinned Docker images with health checks, CPU/GPU compose packaging, CI image builds, and a three-session isolation integration test. After the Phase 6 commit, real STT was implemented: the gateway now buffers microphone PCM, detects utterance boundaries, sends WAV chunks to `stt-service /transcribe`, and routes returned text through the normal router/backend flow. The STT sidecar now uses `faster-whisper` lazily; the first transcription downloads/loads the model. Docker TTS now uses `espeak-ng` and returns real spoken 24 kHz mono WAV; `stub_sine` is only a fallback if no speech engine is available. For local spoken backend answers, use `STT_MODE=real`, `TTS_MODE=real`, and `LLM_MODE=real`; `TTS_MODE=stub` intentionally plays a tone. The flow diagnostic helper at `scripts/flow_log.py` reads `data/events.jsonl` and writes `data/flow-log.md` with per-turn verdicts plus rich handoff detail: user query, Moshi text, router input/decision, backend LLM request/response, harmonizer input/output, injected TTS text, and timing between those steps.

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

Latest handoff logging update:
Gateway event logging now records `transcript.user`, `transcript.moshi`, `router.input`, `llm.answer.request`, `llm.answer.response`, harmonizer request/response events, and final `handoff.inject.text` events. `scripts/flow_log.py` groups those by `utteranceId`/`correlationId` so a fresh test shows whether Moshi answered factually before or during the backend handoff, what text was sent to the backend LLM, what the backend returned, and how long each handoff step took. The gateway image was rebuilt with this change, and Docker gateway/STT/TTS health checks passed. Moshi was not listening on `127.0.0.1:8998` during this update, so the next full live handoff check needs Moshi started first.

Latest issue triage:
`issues.md` now tracks the current real-runtime problems found from `data/events.jsonl` and `data/flow-log.md`: superseded ASK jobs can still inject, TTS injections can overlap or finish out of order, Moshi still produces factual content during backend handoff, stale STT utterances are routed late, transcript windows mix real Moshi fragments with backend-injected text, and Docker real-overlay startup is fragile if STT is not running before the gateway.

Latest plan change:
`PLAN.md` now has a top-level `CHANGES - Qwen3-4B Fast Conversational Layer` section. This is the new project direction and overrides Moshi-first runtime behavior where it conflicts. Qwen3-4B becomes the fast conversational "System 1" layer through STT/TTS. For `CHAT`, Qwen answers normally and briefly. For `ASK`, Qwen stays conversational in `ASK_PENDING` mode without giving factual answers while the backend factual model produces the real answer for later injection. The plan defines the new flow, required components, state machine, prompt contracts, events, metrics, config keys, migration plan, and acceptance criteria.

Latest local Qwen setup:
Ollama 0.30.8 is installed through Homebrew and running as a background service on `127.0.0.1:11434`. `ollama list` shows local model tag `qwen3:4b` with size 2.5 GB, architecture `qwen3`, 4.0B parameters, Q4_K_M quantization, and thinking capability. Native `/api/generate` and OpenAI-compatible `/v1/chat/completions` calls both reach the model. Local tests also showed this Ollama tag still spends output on reasoning even with `think:false`, `/no_think`, CLI `--think=false`, and `--hidethinking`; the Qwen integration must explicitly handle or filter thinking output, use adequate token budgets, or switch/build a non-thinking fast model variant before using it as spoken filler.

Latest Qwen implementation:
The gateway now has an opt-in `VOICE_RUNTIME=qwen` path. In this mode the browser WebSocket starts without connecting to Moshi, inbound PCM goes only to STT, `CHAT` turns call `FastConversationService`, and `ASK` turns start the backend answer job while Qwen speaks an `ASK_PENDING` holding reply through TTS. New config keys are wired for `FAST_LLM_MODE`, `FAST_LLM_BASE_URL`, `FAST_LLM_API_KEY`, `FAST_LLM_MODEL`, `FAST_REPLY_MAX_TOKENS`, `FAST_REPLY_TEMPERATURE`, `FAST_PENDING_MAX_WORDS`, and `FAST_STALE_UTTERANCE_MS`. New events include `fast.reply.*`, `ask.pending.*`, `backend.answer.*`, `backend.inject.*`, and `utterance.dropped_stale_stt`. `OutboundMixer` now prevents overlapping TTS streams, and backend injection waits for fast speech to finish. `scripts/flow_log.py` understands Qwen fast turns and backend timing.

Latest Qwen2.5 diagnostic:
A fresh `VOICE_RUNTIME=qwen` typed ASK run used `FAST_LLM_MODEL=qwen2.5:1.5b`. The model was much faster than `qwen3:4b` and returned normal content with no reasoning field: `fast.reply.response` latency was about 1085 ms and `reasoningPresent=false`. However, it violated truth ownership in `ASK_PENDING` by saying `Well, that's Canberra!` for `what is the capital of australia`. Prompting alone is not sufficient. Next implementation should enforce `ASK_PENDING` with structured safe intents or a hard content guard so the fast layer cannot speak factual answers.

Latest safety design note:
`docs/fast-layer-safety.md` records the chosen three-step strategy for the fast layer: fine-tune for conversational style, sanitize `ASK_PENDING` prompts so the fast model receives only abstract topic context, and verify every fast `ASK_PENDING` draft before TTS. This keeps the fast model interactive instead of hardcoded while preserving backend truth ownership.

Latest fine-tuning workspace:
`fast-layer-finetune/` is the local workspace for the fast model LoRA effort. It documents the target model `Qwen/Qwen2.5-1.5B-Instruct + LoRA`, the workspace purpose, dataset rules, and the major decisions/states for training. It is intentionally a folder inside this repo, not a nested git repository. Local adapter/model outputs are gitignored.

Latest fine-tuning dataset cleanup:
`fast-layer-finetune/data/train.jsonl` and `fast-layer-finetune/data/valid.jsonl` are now the canonical local fine-tuning datasets. The old `train_cl`/`train_ch` comparison files are no longer present. Assistant targets were rewritten to improve spoken phrasing, remove exact duplicate replies, keep `ASK_PENDING` targets non-factual, and preserve the existing state distribution. Validation found 300 train records and 50 validation records, all valid JSONL, all under 20 assistant words, no exact assistant-target duplicates, and no obvious hard factual leaks.

Latest ASK_PENDING sanitizer implementation:
The gateway now has a local `AskPendingPromptSanitizer` for Qwen fast replies. In `ASK_PENDING`, `FastConversationService` no longer sends the raw pending question, latest user text, or transcript window to the fast model. It sends only structured sanitized fields: `STATE`, `SANITIZED_CONTEXT`, `LATEST_USER_INTENT`, and `RULES`. The sanitizer is rule-based Java code, not another LLM call, so it should add negligible latency. Focused tests verify that prompts like `what is the capital of australia` and `whats the cap of aus` become abstract geography prompts without leaking `australia`, `canberra`, or the raw wording.

Latest router slang fix:
The first manual sanitizer check with `whats the cap of aus` showed the heuristic router classified the utterance as `CHAT`, so sanitizer was not reached. `HeuristicRouter` now treats `whats`, `cap of ...`, and `capital of ...` as ASK cues. Regression tests cover `whats the cap of aus` and `cap of aus`.

Latest timing-only log update:
Gateway events now include explicit millisecond timings for router classification, STT sidecar transcription, ASK_PENDING sanitizer, fast Qwen replies, backend answer LLM calls, harmonizer calls, and first-frame/total TTS for fast and backend speech. `scripts/timing_log.py` reads `data/events.jsonl` and writes `data/timing-log.md`, intentionally omitting conversation text and showing only step timings. The report now displays values under one second as `ms`, values above one second as `s` (for example, `2203 ms` becomes `2.20s`), collapses TTS to front/backend totals, repeats the step name inside each timing cell like `front LLM: 2.20s`, and includes a `slowest` column. Older log rows show `-` for newly instrumented columns until the gateway is restarted and fresh turns are recorded with this build.

Latest real runtime start:
The real local Qwen/STT/TTS/backend stack is currently running in detached `screen` sessions: `stt8081`, `tts8082`, and `gateway8080`. Health checks passed for gateway `8080`, STT `8081` (`faster_whisper`, model not yet loaded until mic transcription), TTS `8082` (`macos_say`), and Ollama `11434`. WebSocket smoke tests passed: a typed `hello there` turn routed `CHAT`, Qwen returned `Hello!`, and TTS streamed 7 audio frames; a typed `what is the capital of australia` turn routed `ASK`, sanitizer took `3ms`, fast Qwen returned `Checking for a factual answer...`, backend LLM answered in about `1.01s`, harmonizer in `923ms`, and backend TTS streamed 100 audio frames. `data/timing-log.md` and `data/flow-log.md` were regenerated from the live run.

Latest git publish prep:
Added `RUN.md` with direct local run steps and `EXPLANATION.md` as a compact LLM-readable project overview. `.gitignore` now explicitly excludes `.env.*`, root/runtime `data/`, `gateway/data/`, and the local-only `fast-layer-finetune/` workspace. Runtime logs and fine-tuning files should stay on this machine and should not be pushed. Git remote `origin` is `https://github.com/Ritesh102000/DuplexResponseV1.git`, and `main` tracks `origin/main`.

Latest real-mode runtime check:
On 2026-06-17 19:04 IST, the real local stack was already running in detached `screen` sessions: `stt8081`, `tts8082`, and `gateway8080`. Health checks passed for gateway `8080`, STT `8081` (`faster_whisper`, model loaded), TTS `8082` (`macos_say`), and Ollama `11434`. A typed WebSocket smoke through `ws://127.0.0.1:8080/ws/voice` routed `hello there` as `CHAT`, Qwen returned `Hello!`, TTS streamed audio frames, and `fast.reply.end` arrived.

Latest runtime stop:
On 2026-06-17 19:09 IST, the project gateway/STT/TTS runtime was stopped. No `screen` sessions remain and ports `8080`, `8081`, and `8082` are no longer listening. Ollama was left running on `11434`.

Next exact step:
For product work, implement the verifier before TTS for `ASK_PENDING` fast replies. It should block factual-looking Qwen outputs and replace them with a safe spoken fallback before audio is generated.

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
- Timing-log helper validation: `python3 -m py_compile scripts/timing_log.py`, `python3 scripts/timing_log.py data/events.jsonl --out data/timing-log.md --tail 8`, and `python3 scripts/timing_log.py metrics/fixtures/events.jsonl --out metrics/out/timing-log.md --tail 0`.
- Real Qwen/STT/TTS/backend runtime smoke: started `screen` sessions `stt8081`, `tts8082`, and `gateway8080`; verified `/actuator/health`, STT `/health`, TTS `/health`, Ollama `/api/version`, a typed `CHAT` turn, and a typed `ASK` backend handoff.
- Real-STT validation: `mvn -pl gateway -Dtest=RealSttClientTests test`, `mvn -pl gateway verify`, `python3 -m py_compile stt-service/app/main.py ...`, `docker compose build stt`, `docker compose build gateway`, `curl http://localhost:8081/health`, and a real `/transcribe` call with generated speech audio.
- Real Docker TTS validation: `python3 -m py_compile tts-service/app/main.py`, `docker compose -f docker-compose.yml -f docker-compose.gpu.yml build tts`, recreated TTS, `curl http://localhost:8082/health` returned `engine=espeak_ng`, and `POST /speak` returned a 24 kHz mono WAV.
- Local Qwen runtime validation: `brew services start ollama`, `curl http://localhost:11434/api/version`, `ollama pull qwen3:4b`, `ollama list`, native `/api/generate` probe, and OpenAI-compatible `/v1/chat/completions` probe. Result: model is installed and reachable; thinking output still needs integration handling.
- Qwen implementation validation: `mvn -pl gateway -Dtest=SessionStateMachineTests,QwenRuntimeIntegrationTests test`, `mvn -pl gateway verify`, router-label validation, router eval, `python3 -m py_compile scripts/flow_log.py scripts/timing_log.py`, browser JS syntax checks, `docker compose config`, `docker compose -f docker-compose.yml -f docker-compose.gpu.yml config`, and `git diff --check`.
