# Phase Status

| Phase | Status | Acceptance Tests | Human Checkpoint | Commit |
|---|---|---|---|---|
| Phase 0 - Model decisions + scaffold | Complete | Passed: router-label validation, `mvn verify`, packaged gateway boot | Complete | `phase-0: scaffold project and record model decisions` |
| Phase 1 - Moshi protocol + proxy | Complete | Passed: protocol tests, WebSocket proxy integration tests, reconnect test, `mvn verify` | Complete | `phase-1: document moshi protocol and add ws proxy` |
| Phase 2 - Transcripts + router | Complete | Passed: router-label validation, router eval, heuristic router tests, transcript buffer tests, WebSocket route-decision integration, `mvn verify` | Complete by human request to start Phase 3 | `phase-2: add transcripts and router` |
| Phase 3 - Ask flow end-to-end | Complete | Passed: state-machine tests, ASK-flow WebSocket integration, stale-drop test, supersede test, `mvn -pl gateway verify` | Complete by human request to start Phase 4 | `phase-3: add ask flow job injection` |
| Phase 4 - Suppression + barge-in | Complete | Passed: suppression/barge-in integration tests, `mvn -pl gateway verify` | Complete by human request to start Phase 5; formal 20-question dataset not recorded | `phase-4: add suppression gate and barge-in` |
| Phase 5 - Metrics + evaluation | Complete | Passed: gateway verify, fixture analysis, stub flow judge, router checks, Python/JS syntax, dashboard JSON validation | Complete by human request to start Phase 6; real chart/table still pending | `phase-5: add metrics and evaluation` |
| Phase 6 - Hardening + packaging | Complete | Passed: auth/concurrency tests, gateway verify, router/metrics scripts, Python/JS syntax, compose config, Docker image build | Pending | `phase-6: harden and package demo` |

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
- [x] `MOSHI_MODE=real` bridges browser raw PCM to Moshi Ogg/Opus and decodes Moshi Ogg/Opus back to raw PCM.
- [x] Browser UI has AudioWorklet microphone capture for real Moshi testing through the project gateway.
- [x] Local real-Moshi smoke test through `/ws/voice` returns decoded PCM.
- [x] Moshi `sphn` response audio decodes at 48 kHz and downsamples to browser 24 kHz PCM.
- [x] Browser output path has audio unlock, queued PCM playback, speaker test, and peak logging.
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
- [x] Human checkpoint accepted by request to start Phase 3.

## Phase 3 Acceptance Checklist

- [x] Full state machine covers `IDLE`, `LISTENING`, `MOSHI_TALKING`, `ASK_IN_FLIGHT`, and `INJECTING`.
- [x] ASK jobs run asynchronously with correlation IDs.
- [x] Per-session single-flight/supersede behavior is implemented.
- [x] Backend answer call uses the transcript window and spoken answer prompt.
- [x] Harmonizer supports normal and reintroduced answers.
- [x] Timeout path injects a canned apology.
- [x] Stale policy drops too-old answers and can reintroduce close stale answers.
- [x] `TtsClient` has stub and real-sidecar boundary implementations.
- [x] `tts-service` exists with `/health` and `/speak`.
- [x] Outbound mixer suppresses Moshi audio during TTS injection.
- [x] Browser/control messages include ASK `correlationId`, `inject.start`, and `inject.end`.
- [x] JSONL event log includes ASK/job/injection events.
- [x] State-machine transition test passes.
- [x] Integration test asserts `utterance.end -> router.decision -> job.dispatched -> inject.start -> inject.end`.
- [x] Integration test asserts fake-Moshi audio is absent during injection.
- [x] Stale-drop test passes.
- [x] Supersede test passes.
- [x] `mvn -pl gateway verify` passes.
- [x] Automated real-Moshi audio bridge smoke returns Moshi text and binary PCM through `/ws/voice`.
- [x] Automated real-backend handoff smoke routes typed ASK to the LLM and injects TTS audio through `/ws/voice`.
- [x] Human checkpoint accepted by request to start Phase 4.

## Phase 4 Acceptance Checklist

- [x] Suppression gate is wired during `ASK_IN_FLIGHT`.
- [x] Moshi text-token threshold is configurable.
- [x] Long Moshi answer attempts trigger `suppression.faded`.
- [x] Suppressed Moshi audio fades toward zero over the configured fade window.
- [x] Short Moshi acknowledgment fixtures pass audio through.
- [x] Barge-in detector watches inbound user PCM during `INJECTING`.
- [x] Barge-in cancels active TTS injection and logs `barge_in`.
- [x] Fake Moshi has `ack` and `long-answer` fixtures.
- [x] Integration test asserts long-answer audio energy is near zero after threshold.
- [x] Integration test asserts acknowledgment audio passes through.
- [x] Integration test asserts TTS stream cancels within 500 ms of barge-in.
- [x] `mvn -pl gateway verify` passes.
- [x] Router-label validation and router eval pass.
- [x] Python and JS syntax checks pass.
- [x] Human request to start Phase 5 accepted the checkpoint for phase progression.
- [ ] Formal 20-question real-runtime suppression-rate dataset is recorded.

## Phase 5 Acceptance Checklist

- [x] JSONL events are mirrored into Micrometer counters.
- [x] JSONL events with `latencyMs` are mirrored into Micrometer timers.
- [x] Gateway exposes `/actuator/prometheus`.
- [x] Prometheus scrape config exists.
- [x] Grafana datasource, dashboard provider, and dashboard JSON exist.
- [x] `docker-compose.yml` includes Prometheus and Grafana services.
- [x] `moshi.first_audio` event logging exists for ASK perceived-latency measurement.
- [x] `metrics/analyze.py` reads JSONL events and emits `latency_chart.png`.
- [x] `metrics/analyze.py` emits summary JSON, per-ASK latency CSV, and router confusion matrix CSV.
- [x] `metrics/judge_flow.py` runs against the committed sample set in stub mode.
- [x] CI runs fixture analysis and stub flow judge.
- [x] `mvn -pl gateway verify` passes.
- [x] Metrics fixture analysis passes.
- [x] Stub flow judge passes.
- [x] Router-label validation and router eval pass.
- [x] Python and JS syntax checks pass.
- [x] Grafana dashboard JSON validates.
- [x] Human request to start Phase 6 accepted the checkpoint for phase progression.
- [ ] Human real-runtime metrics chart/table is generated and pasted into `README.md`.

## Phase 6 Acceptance Checklist

- [x] `/ws/voice` supports opt-in bearer-token auth.
- [x] Browser client can pass token via URL/localStorage.
- [x] Browser client auto-reconnects after non-manual socket drops.
- [x] Real sidecar modes perform startup `/health` retries.
- [x] Real LLM client retries transient failures.
- [x] ASK jobs inject a spoken fallback when answer/harmonizer calls fail.
- [x] Moshi real-client connection failures surface to callbacks and reset state.
- [x] Gateway, STT, and TTS Dockerfiles use pinned base tags and health checks.
- [x] CPU compose stack includes gateway, STT, TTS, Prometheus, and Grafana health checks.
- [x] GPU compose overlay supports Mac-hosted MLX Moshi, optional Linux Moshi, and optional Ollama.
- [x] CI builds gateway, STT, and TTS images.
- [x] README includes architecture, metrics, hard-problems, packaging, auth, and future-scope sections.
- [x] Bearer-token handshake integration tests pass.
- [x] Three-session isolation integration test passes.
- [x] `mvn -pl gateway verify` passes.
- [x] Router-label validation and router eval pass.
- [x] Metrics fixture analysis and stub judge pass.
- [x] Python and JS syntax checks pass.
- [x] Compose config validation passes.
- [x] Docker image build passes.
- [x] Post-checkpoint flow diagnostic helper writes `data/flow-log.md` from `data/events.jsonl`.
- [x] Post-checkpoint real STT path converts microphone PCM to router utterances through `stt-service`.
- [x] `RealSttClientTests` pass.
- [x] `stt-service /transcribe` returns real faster-whisper text for generated speech audio.
- [x] Post-checkpoint Docker TTS speaks through `espeak-ng` instead of `stub_sine`.
- [ ] Human GPU/full-runtime compose checkpoint is complete.
- [ ] Three-minute demo video is recorded.
