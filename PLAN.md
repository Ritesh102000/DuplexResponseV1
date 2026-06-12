# PLAN - Two-Tier Voice Assistant Demo

Use this file as the canonical project plan and restart packet in Codex on the Mac.

Read all sections before writing code.

---

# Project Memory / LLM Handoff Rules

Every coding agent or LLM that works on this project must treat `PLAN.md` as the source of truth.

Before writing code, read:
- `PLAN.md`
- `AGENTS.md` if it exists
- `PROJECT_CONTEXT.md` if it exists
- `docs/PROJECT_MEMORY.md` if it exists
- `docs/WORK_LOG.md` if it exists
- `docs/PHASE_STATUS.md` if it exists
- `docs/decisions.md` if it exists

During Phase 0, create the project-memory files listed in the repository layout. Keep them current throughout the project:
- `AGENTS.md` explains mandatory operating rules for future coding agents.
- `PROJECT_CONTEXT.md` is the short current-state handoff at the repo root.
- `docs/PROJECT_MEMORY.md` is the living summary of current phase, completed work, architecture, decisions, blockers, known issues, and next exact step.
- `docs/WORK_LOG.md` is the append-only chronological history of work performed.
- `docs/PHASE_STATUS.md` tracks Phase 0 through Phase 6 completion, acceptance tests, checkpoint status, blockers, and commit hashes.

Before stopping, committing, completing a phase, handing off to another agent, or reporting a blocker, update the project-memory files so a new LLM can continue without missing context. Runtime JSONL event logs are for demo metrics only; they do not replace these project-memory files.

---

# 1. Original Implementation Spec

# IMPLEMENTATION SPEC — Two-Tier Voice Assistant (Demo)
### For an AI coding agent. Read this entire document before writing any code.

---

## 0. How to use this document (agent operating rules)

1. **`PLAN.md` is the canonical source of truth.** Read it before coding and keep it synchronized with the current project direction when the plan changes.
2. **Implement strictly phase by phase (Phase 0 → 6).** At the end of each phase, STOP. Output a summary of what was built, run the phase's acceptance tests, update the project-memory files, and wait for the human to complete the HUMAN CHECKPOINT before continuing.
3. **Maintain project-memory files.** Create them in Phase 0 and update them after every meaningful change, failed attempt, decision, checkpoint, blocker, or handoff.
4. **Do not invent protocols, fields, or endpoints.** All internal contracts are defined in §5. The one external protocol (Moshi's WebSocket) must be extracted from the pinned source code in Phase 1 — never guessed.
5. **No GPU is available to you.** All code you write must build, run, and pass tests in **stub mode** (`MOSHI_MODE=stub`, `STT_MODE=stub`, `LLM_MODE=stub`). Real-model runs are done by the human at checkpoints.
6. **Engineering choices are locked (§2A); model choices are NOT yours to make.** Before writing any code, you MUST conduct the **Model Decision Interview (§2B)** with the human — ask the questions, wait for answers, record them in `docs/decisions.md`, and configure `.env.example` from those answers. Never assume a provider, model name, GPU size, or voice. If an answer later turns out infeasible, stop and re-ask — do not silently substitute.
7. **Every external system sits behind a Java interface** (§5.6) with a stub implementation and a real implementation selected by configuration. This is what makes the model answers swappable without code changes.
8. One git commit minimum per phase, message format: `phase-N: <summary>`.
9. Keep all secrets/config in `.env` (§4); never hardcode.
10. **Follow the gateway internal architecture in §3.1 exactly** — component names, package placement, and threading rules. It is designed so the human (a Spring Boot developer) can navigate and extend the code.

---

## 1. Project summary

A real-time voice assistant with a two-tier "System 1 / System 2" architecture:

- **Moshi** (Kyutai's open-source full-duplex speech-to-speech model) owns the live audio channel: turn-taking, small talk, acknowledgments, sub-second responses.
- A **router** classifies each user utterance: `CHAT` (Moshi handles alone), `ASK` (delegated to a backend reasoning LLM), `ACT` (detected but answered with a canned "coming soon" — full act flow is future scope, not built).
- For `ASK`: the backend answers in 8–15s while Moshi holds the floor; a **harmonizer** rewrites the answer into ≤2 short spoken sentences; the gateway speaks it via TTS spliced into the audio stream ("voice-matched injection").
- **Suppression** is enforced at the gateway: while an ask is in flight, Moshi's substantive answer attempts are faded out; only acknowledgment-length output passes.
- A **Spring Boot gateway** is the centerpiece: binary WebSocket audio proxy, session state machine, async job queue with correlation IDs, stale-answer policy, metrics.
- Headline metric: **perceived latency < 1s while true answer latency is 8–15s**, plus router accuracy, suppression rate, flow-break rate.

### Demo definition of done
1. Full-duplex voice conversation with Moshi through the gateway (not Moshi's own UI).
2. Live router labeling utterances CHAT/ASK/ACT.
3. Ask flow end-to-end: acknowledge <1s → real answer spoken ~10s later as a continuation.
4. Suppression working and measured.
5. Stale answers dropped or reintroduced ("about your earlier question…").
6. Metrics chart: perceived vs true latency, router confusion matrix, suppression rate, flow-break rate.

---

## 2A. Locked engineering stack (no alternatives — do not change)

| Component | Decision | Notes |
|---|---|---|
| Gateway | **Spring Boot 3.x, Java 21**, Maven | Virtual threads enabled (`spring.threads.virtual.enabled=true`). The gateway is the project's core artifact |
| WebSockets | `spring-boot-starter-websocket`, **raw binary handlers** | No STOMP, no SockJS |
| HTTP client | Spring `WebClient` + Resilience4j (timeouts, retry) | |
| Session store | **Caffeine** in-memory cache | sessionId → SessionState |
| Job queue | In-process: virtual-thread `ExecutorService` + `ConcurrentHashMap<String, Job>` | **No Kafka/RabbitMQ/Redis** |
| Moshi serving | Kyutai's **Python server** (`pip install moshi`, `python -m moshi.server`) | Pin the version. Protocol source of truth: https://github.com/kyutai-labs/moshi |
| LLM access pattern | One **OpenAI-compatible chat-completions client** for answer/router/harmonizer/judge | Provider-agnostic via `LLM_BASE_URL` — hosted APIs and Ollama both work with zero code change |
| Router fallback | Regex/keyword heuristic in Java | Used when `LLM_MODE=stub` and as a timeout fallback |
| STT wrapper | Python **FastAPI sidecar** in `stt-service/` with **Silero VAD** for end-of-utterance segmentation | Engine chosen in interview (default faster-whisper) |
| TTS wrapper | Python **FastAPI sidecar** in `tts-service/` (`/speak`, text in → 24kHz PCM WAV out) | Engine/voice chosen in interview (default Piper) |
| Client | Static **HTML + vanilla JS**: AudioWorklet mic capture → 24kHz PCM frames over WS; Web Audio playback; debug panel | Served from `src/main/resources/static/` |
| Metrics | **Micrometer → Prometheus → Grafana** AND append-only **JSONL event log** (§5.5) + `metrics/analyze.py` (matplotlib) | JSONL+script is the primary deliverable; Grafana is the live dashboard |
| Packaging | Docker Compose: `gateway`, `stt`, `tts`, `prometheus`, `grafana`, optional `moshi` (GPU), optional `ollama` | CI never starts `moshi`/`ollama` |
| CI | GitHub Actions: build, unit + integration tests (stub mode), image build | |

## 2B. Model Decision Interview — ASK THE HUMAN FIRST (Phase 0, task 1)

Ask these questions **one numbered list, all at once**, wait for answers, then write `docs/decisions.md` (question → answer → consequence) and generate `.env.example` from it. Recommended defaults are given so the human can simply reply "default" to any question.

| # | Question | Why it matters | Recommended default |
|---|---|---|---|
| 1 | What GPU will run Moshi (model + VRAM)? Local machine or rented cloud (RunPod/Lambda)? Or no GPU yet? | Decides Moshi precision (bf16 needs ~16GB; q8 fits smaller), whether STT/TTS can share the GPU, and whether `docker-compose.gpu.yml` targets localhost or a remote host | Rented 24GB (RTX 3090/4090 class) when needed; develop in stub mode until then |
| 2 | Moshi voice variant: `moshiko` (male) or `moshika` (female)? | TTS injection voice must match it | moshiko |
| 3 | Backend **answer** model: hosted API (which provider + model? do you have a key and budget?) or local via Ollama (which model)? | Quality of ask-flow answers and the "self-hosted" pitch vs convenience | Hosted API, mid-tier model — zero VRAM cost, best harmonizer quality |
| 4 | **Router** model: small/fast hosted model, local small model via Ollama, or heuristic-only to start? | Router runs on every utterance — must return in <1.5s and be cheap | Small/fast model from the same provider, heuristic fallback always on |
| 5 | Harmonizer + LLM-judge: reuse the answer model? | Simplicity vs cost | Yes, reuse |
| 6 | STT engine: faster-whisper on CPU (`small` model) or GPU? Primary spoken language/accent? | Latency of end-of-utterance detection; whisper model choice for Indian-English accuracy | faster-whisper `small`, CPU, English (Indian accent — note for model size upgrade if accuracy poor) |
| 7 | TTS: Piper (CPU, default) — which voice (must match Q2 gender)? | The injection seam quality | Piper, a natural male en voice matching moshiko |
| 8 | Monthly API budget cap for LLM calls during dev + evals? | Sizes the eval runs (router eval = 140 calls; judge eval = ~40 calls) | State a number; agent adds a per-session call cap config |
| 9 | Where will the final demo run: your local GPU box, a rented GPU instance, or hybrid (Moshi on GPU box + hosted LLM)? | Determines compose files, networking, and the README's deployment section | Hybrid: GPU box for Moshi/STT/TTS, hosted API for LLM |

**Do not proceed past this interview without explicit answers.** Re-confirm any answer that conflicts with hardware limits (e.g., bf16 Moshi + Ollama 8B on one 16GB card does not fit — flag it, propose quantization, ask again).

---

## 3. Repository layout

```
voice-two-brains/
├── AGENTS.md                   # mandatory operating rules for future coding agents
├── PROJECT_CONTEXT.md          # compact current-state handoff for humans and LLMs
├── README.md
├── docs/
│   ├── PROJECT_MEMORY.md       # living project context, blockers, known issues, next step
│   ├── WORK_LOG.md             # append-only chronological project history
│   ├── PHASE_STATUS.md         # phase completion and checkpoint tracker
│   ├── architecture.md          # diagrams (Mermaid)
│   ├── moshi-protocol.md        # Phase 1 deliverable — extracted, not invented
│   └── eval/router-labels.jsonl # Phase 0 labeled utterance set
├── gateway/                     # Spring Boot app (Maven)
│   └── src/main/java/com/voicedemo/gateway/
│       ├── ws/                  # browser WS handler, moshi WS client
│       ├── session/             # SessionState, state machine
│       ├── router/              # RouterService + impls
│       ├── jobs/                # JobQueue, correlation, stale policy
│       ├── llm/                 # OpenAI-compatible client, harmonizer
│       ├── speech/              # SttClient, TtsClient, audio mixing/ducking
│       ├── metrics/             # EventLogger (JSONL) + Micrometer
│       └── config/
├── stt-service/                 # FastAPI + VAD + STT engine (per interview)
├── tts-service/                 # FastAPI + TTS engine (per interview)
├── stubs/fake-moshi/            # Python WS server replaying recorded frames
├── web/ → gateway static resources
├── metrics/analyze.py
├── docker-compose.yml
├── docker-compose.gpu.yml       # adds real moshi (+ ollama if chosen)
└── .github/workflows/ci.yml
```

### 3.1 Gateway internal architecture (Spring Boot — the heart of the project)

```
                    ┌──────────────────────── Spring Boot Gateway ────────────────────────┐
 Browser ──WS──►    │ BrowserSocketHandler (ws/)                                          │
 (PCM 24k frames    │   │ binary → AudioInboundPipeline      │ JSON ← ControlMessageSender│
  + JSON control)   │   ▼                                    ▲                            │
                    │ AudioInboundPipeline (speech/)         │                            │
                    │   ├─ tee ──► MoshiClient (ws/)  ──Opus──► Moshi server (GPU)        │
                    │   └─ tee ──► SttClient (speech/) ──► stt-service sidecar            │
                    │                  │ onUtterance(text, ts)                            │
                    │                  ▼                                                  │
                    │ SessionStateMachine (session/) ◄──── events from ALL components     │
                    │   ├─► RouterService (router/) ──► LlmClient (llm/)                  │
                    │   ├─► AskJobService (jobs/)   ──► LlmClient ──► Harmonizer (llm/)   │
                    │   │      (virtual thread per job, correlationId, stale policy)      │
                    │   └─► EventLogger + MetricsFacade (metrics/)                        │
                    │                                                                     │
                    │ OutboundMixer (speech/)  ◄── Moshi audio (Opus→PCM, after           │
 Browser ◄──WS──    │   single PCM stream          SuppressionGate)                       │
                    │   to browser            ◄── TTS injection stream (TtsClient)        │
                    └─────────────────────────────────────────────────────────────────────┘
```

**Component responsibilities (one class ↔ one job):**
- `BrowserSocketHandler` — accepts `/ws/voice`, owns the client connection, routes binary frames to the pipeline and JSON control messages out. Zero business logic.
- `AudioInboundPipeline` — per-session; tees every inbound PCM frame to MoshiClient and SttClient. Never blocks.
- `MoshiClient` — WS client to Moshi; PCM↔Opus transcoding; surfaces `onAudio` / `onText` callbacks.
- `SuppressionGate` — sits between Moshi's decoded audio and the mixer; consults the state machine; applies the §5.7 fade.
- `OutboundMixer` — the ONLY writer of audio to the browser. Two sources (Moshi-gated, TTS); implements ducking/splicing; emits `inject.start/end`.
- `SessionStateMachine` — single synchronized event-handling method per session (`onEvent(SessionEvent)`); implements the §5.2 table verbatim; the only place state transitions happen.
- `AskJobService` — dispatch, correlation, supersede, timeout, stale policy; completion posts a `JobResultEvent` back to the state machine (never touches audio directly).
- `EventLogger` — append-only JSONL writer; every component calls it; also mirrors to Micrometer.

**Threading rules (enforce in code review):**
1. Audio path (pipeline → moshi → gate → mixer) runs on a **per-session single-threaded executor** — preserves frame order, no locks on the hot path.
2. LLM/STT/TTS/job work runs on **virtual threads** — never on the audio executor.
3. Components communicate with the state machine only via posted events; the state machine never performs I/O itself (it triggers it via callbacks executed off-thread).

**Spring wiring:** `Stub*` vs `Real*` beans selected with `@ConditionalOnProperty` on `MOSHI_MODE` / `STT_MODE` / `TTS_MODE` / `LLM_MODE`. Prompts live in `resources/prompts/{router,answer_style,harmonizer,judge}.txt` loaded at startup.

### 3.2 Deployment topologies (final choice comes from interview Q9)

```
A. HYBRID (recommended)                      B. FULLY SELF-HOSTED
GPU box (24GB): moshi                        GPU box (24GB): moshi(q8) + ollama
CPU containers: gateway, stt, tts,           CPU containers: gateway, stt, tts,
                prometheus, grafana                          prometheus, grafana
LLM: hosted API over HTTPS                   LLM: ollama (OpenAI-compatible URL)

C. DEV MODE (no GPU, what the agent uses daily)
Everything CPU: gateway + stt + tts + fake-moshi + stub LLM — full test suite runs here
```

---

## 4. Configuration (`.env`)

> Model-related values below are placeholders — the agent fills `.env.example` from the §2B interview answers recorded in `docs/decisions.md`.

```
MOSHI_MODE=stub|real        MOSHI_WS_URL=ws://moshi:8998/api/chat
STT_MODE=stub|real          STT_URL=http://stt:8081
TTS_MODE=stub|real          TTS_URL=http://tts:8082
LLM_MODE=stub|real          LLM_BASE_URL=   LLM_API_KEY=
LLM_MODEL_ANSWER=           LLM_MODEL_ROUTER=
ASK_TIMEOUT_MS=20000        STALE_TURN_LIMIT=2
EVENT_LOG_PATH=/data/events.jsonl
```

---

## 5. Contracts (source of truth — implement exactly)

### 5.1 Browser ↔ Gateway WebSocket (`/ws/voice`)
- **Binary frames**: raw audio, 24kHz, 16-bit signed PCM, mono, 80ms chunks (3840 bytes). Both directions.
- **Text frames**: JSON control messages:

```json
{"type":"session.start","sessionId":"<uuid>","ts":1718200000000}
{"type":"transcript.user","sessionId":"…","utteranceId":"u-17","text":"what is the capital of australia","ts":…}
{"type":"transcript.moshi","sessionId":"…","text":"hmm good question","ts":…}
{"type":"router.decision","sessionId":"…","utteranceId":"u-17","label":"ASK","correlationId":"c-9","ts":…}
{"type":"inject.start","sessionId":"…","correlationId":"c-9","ts":…}
{"type":"inject.end","sessionId":"…","correlationId":"c-9","ts":…}
{"type":"error","code":"BACKEND_TIMEOUT","message":"…","ts":…}
```
The web client renders these in a debug panel — this is how the demo is *shown*, not just heard.

### 5.2 Session state machine
States: `IDLE, LISTENING, MOSHI_TALKING, ASK_IN_FLIGHT, INJECTING`.

| Current | Event | Next | Side effects |
|---|---|---|---|
| IDLE | client WS open + moshi WS open | LISTENING | emit session.start |
| LISTENING | moshi audio starts | MOSHI_TALKING | |
| MOSHI_TALKING | moshi audio idle >300ms | LISTENING | |
| LISTENING / MOSHI_TALKING | router → ASK | ASK_IN_FLIGHT | mint correlationId, dispatch job, **suppression gate ON** |
| ASK_IN_FLIGHT | job result + fresh (§5.4) | INJECTING | duck Moshi audio, stream TTS |
| ASK_IN_FLIGHT | job result + stale-reintroduce | INJECTING | harmonizer adds "about your earlier question…" |
| ASK_IN_FLIGHT | job result + stale-drop | LISTENING | log drop, gate OFF |
| ASK_IN_FLIGHT | job timeout | INJECTING | inject canned apology |
| INJECTING | TTS stream ends | LISTENING | un-duck Moshi, gate OFF, emit inject.end |
| INJECTING | user speech (VAD) >400ms | LISTENING | **barge-in**: stop TTS, log abandoned |
| any | moshi WS drop | IDLE | reset session, notify client |

Concurrent asks: max 1 in flight per session; a new ASK while one is in flight supersedes it (old job result → drop).

### 5.3 Internal job envelope
```json
{"correlationId":"c-9","sessionId":"…","utteranceId":"u-17",
 "transcriptSnapshot":[{"speaker":"user","text":"…"},{"speaker":"moshi","text":"…"}],
 "userTurnIndexAtDispatch":17,"dispatchedAt":1718200000000}
```

### 5.4 Stale policy
On job completion: `delta = currentUserTurnIndex - userTurnIndexAtDispatch`.
`delta <= STALE_TURN_LIMIT` → inject. `delta <= STALE_TURN_LIMIT + 2` → reintroduce. Else → drop.

### 5.5 Event log (JSONL, one event per line) — feeds `metrics/analyze.py`
Event names (exact strings): `utterance.end`, `moshi.first_audio`, `router.decision`, `job.dispatched`, `job.completed`, `inject.start`, `inject.end`, `suppression.faded`, `barge_in`, `job.dropped_stale`.
Every event: `{event, sessionId, ts, correlationId?, utteranceId?, label?, latencyMs?}`.
Headline chart = per ASK: `moshi.first_audio − utterance.end` (perceived) vs `inject.start − utterance.end` (true), plotted together.

### 5.6 Service interfaces (gateway)
```java
interface MoshiClient   { void connect(SessionState s); void sendAudio(byte[] pcm);
                          /* callbacks: onAudio(byte[]), onText(String) */ }
interface SttClient     { /* feed PCM; callback onUtterance(String text, long endTs) */ }
interface TtsClient     { Flux<byte[]> speak(String text); }
interface LlmClient     { String chat(String model, List<Msg> messages, double temp, int maxTokens); }
interface RouterService { RouteDecision classify(List<TranscriptLine> window, String utterance); }
interface Harmonizer    { String harmonize(String rawAnswer, List<TranscriptLine> recent, boolean reintroduce); }
```
Each has `Stub*` and `Real*` implementations chosen by `*_MODE` config.

### 5.7 Suppression gate (gateway-level — Moshi takes no system prompt)
While ASK_IN_FLIGHT: watch Moshi's text-token stream per utterance. If cumulative tokens for Moshi's current utterance ≤ 8 tokens → pass audio (acknowledgment). If it exceeds 8 tokens → fade its audio to zero over 250ms, keep consuming silently, log `suppression.faded`. Threshold configurable.

---

## 6. External system: Moshi (Phase 1 protocol-discovery task)

Known facts: the Python server exposes a WebSocket (default port 8998); audio is streamed both ways (Opus-encoded); Moshi also streams its own text tokens (its "inner monologue" = transcript of its speech). **Exact framing, opcodes, handshake, and Opus packetization must be extracted by reading the pinned `moshi` package source (server + web client) — write findings into `docs/moshi-protocol.md` BEFORE implementing `RealMoshiClient`.** The gateway transcodes: browser PCM ↔ Opus toward Moshi (use Java `opus-jni` or `org.concentus` pure-Java Opus — prefer Concentus to avoid native build pain).

`stubs/fake-moshi/` mirrors the documented protocol exactly and replays canned audio+text sequences from fixture files; it is the test double for ALL CI tests.

---

## 7. Phases

> Each phase: build → run acceptance tests (all in stub mode unless marked HUMAN) → STOP for checkpoint.

### Phase 0 — Model interview + scaffolding & design artifacts
**Build:** **FIRST: create the project-memory files (`AGENTS.md`, `PROJECT_CONTEXT.md`, `docs/PROJECT_MEMORY.md`, `docs/WORK_LOG.md`, `docs/PHASE_STATUS.md`) and record the initial handoff context.** Then conduct or apply the Model Decision Interview (§2B), write `docs/decisions.md`, and generate `.env.example` from it. Then: repo layout (§3), Maven Spring Boot skeleton that boots, docker-compose with healthchecks (gateway only for now), CI pipeline running `mvn verify`, `docs/architecture.md` with the §3.1 component diagram + Mermaid sequence diagrams (happy ask, stale, barge-in), and `docs/eval/router-labels.jsonl` — **140 labeled utterances** (50 CHAT, 50 ASK, 40 ACT, including ≥20 deliberately ambiguous, e.g. "can you believe how expensive flights are" = CHAT vs "how expensive are flights to Goa" = ASK).
**Acceptance:** project-memory files exist; `AGENTS.md` instructs future LLMs to read `PLAN.md` and update the memory files; `WORK_LOG.md` has an initial entry; `PHASE_STATUS.md` reflects Phase 0 status; `PROJECT_MEMORY.md` accurately states current phase, completed work, blockers, and next exact step; `docs/decisions.md` exists with all 9 answers; CI green; gateway boots; JSONL validates against a small schema-check script.
**HUMAN CHECKPOINT:** confirm decisions.md reflects your answers; review label set boundaries.

### Phase 1 — Moshi protocol doc + audio pass-through proxy
**Build:** `docs/moshi-protocol.md` from pinned source; `fake-moshi` stub honoring it; `RealMoshiClient` + `StubMoshiClient`; browser WS endpoint; PCM↔Opus transcoding; transparent proxy browser↔gateway↔moshi; minimal web client (mic capture, playback, debug panel showing §5.1 messages); session lifecycle (IDLE↔LISTENING wiring only).
**Acceptance (stub):** integration test streams a fixture WAV through the proxy and asserts byte-equivalent audio out + text frames forwarded; reconnect test (stub drops, session resets).
**HUMAN CHECKPOINT (GPU):** run real Moshi, hold a full-duplex conversation through the gateway; confirm no perceptible added latency.

### Phase 2 — Transcripts + Router
**Build:** `stt-service` sidecar (faster-whisper + Silero VAD, returns utterances with end timestamps) + `SttClient`; per-session transcript ring buffer (user lines from STT, Moshi lines from its text stream); `RouterService`: `RealRouter` (LLM, strict JSON output `{"label":"CHAT|ASK|ACT","confidence":0..1}`, 1.5s timeout → heuristic fallback) and `HeuristicRouter`; ACT → canned spoken reply path (stub TTS for now); `router.decision` events to client + event log; offline eval runner: `mvn -Pe2e exec` or a script that runs the labeled set through the router and prints accuracy + confusion matrix.
**Acceptance (stub):** unit tests for heuristic router; eval runner executes against stub LLM (heuristic) and produces the matrix; transcript buffer ordering test with interleaved speakers.
**HUMAN CHECKPOINT:** run eval with a real LLM key; record accuracy in README (target ≥90% on CHAT/ASK).

### Phase 3 — Ask flow end-to-end (the heart)
**Build:** JobQueue (virtual threads, correlationIds, per-session single-flight + supersede); backend answer call (transcript window + spoken-register style guide prompt: ≤2 sentences, no lists, reference what was just said); Harmonizer (separate prompt, handles `reintroduce` prefix); `tts-service` sidecar (Piper) + `TtsClient`; audio ducking/splicing in the outbound mixer (duck Moshi −∞ over 250ms, stream TTS PCM, restore); stale policy §5.4; timeout → canned apology injection; full state machine §5.2; all events of §5.5 emitted.
**Acceptance (stub):** state-machine exhaustive transition unit tests; integration test with fake-moshi + stub LLM (fixed 2s delayed answer) asserting exact event sequence `utterance.end → router.decision(ASK) → job.dispatched → inject.start → inject.end` and that fake-moshi audio is absent from output during injection; stale test (advance turns past limit → `job.dropped_stale`); supersede test.
**HUMAN CHECKPOINT (GPU + real LLM):** ask a factual question; Moshi acknowledges <1s; harmonized answer spoken ~8–15s later. Record screen+audio — this is the demo's money shot.

### Phase 4 — Suppression + barge-in
**Build:** suppression gate §5.7 wired to ASK_IN_FLIGHT; barge-in: VAD-on-user during INJECTING → stop TTS, log, return floor; suppression fixtures in fake-moshi (one replay where "Moshi" gives a long answer to a delegated question).
**Acceptance (stub):** test that the long-answer fixture is faded (output audio energy ≈ 0 after threshold) and `suppression.faded` logged; acknowledgment fixture passes through; barge-in test asserts TTS stream cancelled within 500ms.
**HUMAN CHECKPOINT (GPU):** 20 scripted delegated questions; record suppression rate.

### Phase 5 — Metrics + evaluation
**Build:** Micrometer counters/timers mirroring §5.5; Prometheus + Grafana provisioned dashboard (JSON in repo); `metrics/analyze.py` producing: headline perceived-vs-true latency chart (PNG), suppression rate, router confusion matrix from event log; LLM-as-judge script: for each (transcript-window, injected-answer) pair ask the LLM "does this read as a natural spoken continuation? yes/no + reason", output flow-break rate.
**Acceptance (stub):** `analyze.py` runs on a committed sample `events.jsonl` fixture and emits the chart; judge script runs against stub LLM.
**HUMAN CHECKPOINT:** generate real numbers from Phase 3/4 GPU sessions; paste chart + table into README.

### Phase 6 — Hardening + packaging
**Build:** error paths (LLM 429/timeout fallback, moshi drop → clean reset + client auto-reconnect, sidecar healthchecks with startup retries); `docker-compose.yml` (CPU services) + `docker-compose.gpu.yml` (adds moshi, ollama) — one-command bring-up; pinned versions everywhere; simple bearer-token check on `/ws/voice`; README final: architecture diagram, headline chart, metrics table, "hard problems" section (suppression, floor-holding, stale answers, one-voice-two-brains, barge-in), future scope; 2–3 concurrent-session isolation test against fake-moshi.
**Acceptance (stub):** `docker compose up` on CPU brings up gateway+stt+tts+prometheus+grafana healthy with `MOSHI_MODE=stub`; concurrency test green; CI builds all images.
**HUMAN CHECKPOINT (GPU):** full-stack run via `docker-compose.gpu.yml`; record the 3-minute demo video.

---

## 8. Non-goals (do NOT build — pitch as future scope in README)
- Act flow execution (action templates, slot-filling, confirmation, real email/message sending) — router *detects* ACT only.
- RAG / web search behind the ask flow.
- Kyutai TTS voice matching; true in-model injection into Moshi.
- Barge-in *resume* ("as I was saying…") — abandon-only is fine.
- Multi-GPU scaling, persistence, auth beyond a bearer token.

## 9. Pitfalls — explicit prohibitions
1. Do not guess Moshi's wire format; §6 discovery task is mandatory and blocks Phase 1 code.
2. Do not add Kafka, Redis, STOMP, SockJS, React, or a build-step frontend.
3. Do not put model calls on the audio hot path; audio proxying must never block on LLM/STT/TTS calls.
4. Do not let any test require a GPU, an API key, or network access to model providers — stubs only in CI.
5. Do not collapse router/harmonizer/answer into one prompt; they are three distinct calls with distinct prompts in `resources/prompts/`.
6. Do not skip the debug panel (§5.1) — the demo is evaluated visually as much as audibly.

---

# 2. Model and Demo Decisions Already Made

Date: 2026-06-12

These decisions were made in the earlier Codex planning conversation and should override the defaults in the original implementation spec.

## 1. Hardware
Answer:
Apple Mac M4 with 16 GB unified memory.

Consequence:
Develop and demo locally where possible. No NVIDIA GPU is assumed for the main development target. Stub mode must still be the default for CI and normal development.

## 2. Moshi Runtime
Answer:
Run Moshi locally using Apple Silicon MLX q4.

Consequence:
Use `kyutai/moshiko-mlx-q4` as the intended local real Moshi target.
Cloud GPU remains a fallback only if local latency or memory is not acceptable.

Reference:
https://huggingface.co/kyutai/moshiko-mlx-q4

Supporting note:
The Hugging Face model card for `kyutai/moshiko-mlx-q4` describes it as an MLX Mac 4-bit checkpoint and shows Apple Silicon local inference through `moshi_mlx`.

## 3. Moshi Voice Variant
Answer:
`moshiko` male.

Consequence:
TTS injection voice should be a natural male English Piper voice that roughly matches moshiko.

## 4. Backend Answer Model
Answer:
Hosted OpenAI-compatible API.

Chosen model:
`gpt-5.4-mini`

Consequence:
The gateway must use the OpenAI-compatible chat-completions abstraction from the spec.
Do not hardcode OpenAI-only logic beyond config defaults. The provider should remain swappable through `LLM_BASE_URL`, `LLM_API_KEY`, and model config.

Reference:
https://developers.openai.com/api/docs/models

Supporting note:
OpenAI's current model documentation recommends GPT-5.4 mini/nano for lower-latency, lower-cost workloads, while GPT-5.5 is the flagship choice for the hardest work.

## 5. Router Model
Answer:
Use the same hosted API model class, with heuristic fallback.

Chosen model:
`gpt-5.4-mini`

Consequence:
Router runs via LLM in real mode, but heuristic routing remains available in stub mode and as a timeout fallback.

## 6. Harmonizer and LLM Judge
Answer:
Reuse the answer model.

Chosen model:
`gpt-5.4-mini`

Consequence:
Use one model setting for answer/harmonizer/judge unless later changed by config.

## 7. STT
Answer:
`faster-whisper small` on CPU with Silero VAD.

Language/accent:
English, Indian accent expected.

Consequence:
Do not assume GPU STT. If accuracy is poor later, upgrade model size only after human confirmation.

## 8. TTS
Answer:
Piper CPU, natural male English voice.

Consequence:
Keep TTS local and CPU-friendly for the Mac M4 16 GB setup.

## 9. API Budget
Answer:
`$20/month`.

Consequence:
Add budget/call-cap config. Keep eval runs modest.

## 10. Final Demo Topology
Answer:
Mac + API.

Consequence:
Run Moshi/STT/TTS locally on Mac. Use hosted OpenAI-compatible API for backend LLM.

---

# 3. Expected `.env.example`

Use these values when generating `.env.example` in Phase 0.

```
MOSHI_MODE=stub
MOSHI_WS_URL=ws://localhost:8998/api/chat

STT_MODE=stub
STT_URL=http://localhost:8081

TTS_MODE=stub
TTS_URL=http://localhost:8082

LLM_MODE=stub
LLM_BASE_URL=https://api.openai.com/v1
LLM_API_KEY=
LLM_MODEL_ANSWER=gpt-5.4-mini
LLM_MODEL_ROUTER=gpt-5.4-mini

ASK_TIMEOUT_MS=20000
STALE_TURN_LIMIT=2
EVENT_LOG_PATH=./data/events.jsonl

API_MONTHLY_BUDGET_USD=20
LLM_SESSION_CALL_CAP=100

MOSHI_LOCAL_RUNTIME=mlx-q4
MOSHI_HF_REPO=kyutai/moshiko-mlx-q4
MOSHI_VOICE=moshiko
```

---

# 4. Recommended `docs/decisions.md` Content

When Phase 0 is implemented, create `docs/decisions.md` from the decisions above.

Suggested structure:

```
# Model Decisions

Date: 2026-06-12

## Summary
- Hardware: Apple Mac M4, 16 GB unified memory.
- Moshi: local Apple Silicon MLX q4, `kyutai/moshiko-mlx-q4`.
- Voice: `moshiko` male.
- Backend LLM: hosted OpenAI-compatible API.
- Models: `gpt-5.4-mini` for answer, router, harmonizer, judge.
- STT: faster-whisper small CPU with Silero VAD.
- TTS: Piper CPU, natural male English voice.
- API budget: $20/month.
- Demo topology: Mac + API.

## Interview Answers

1. What GPU/hardware will run Moshi?
   Answer: Apple Mac M4 with 16 GB unified memory.
   Consequence: Use local Apple Silicon MLX q4 for real Moshi; keep stub mode default for CI.

2. Moshi voice variant?
   Answer: moshiko male.
   Consequence: Pick a male Piper voice for injection.

3. Backend answer model?
   Answer: Hosted OpenAI-compatible API, `gpt-5.4-mini`.
   Consequence: Good quality/cost tradeoff with no local LLM memory pressure.

4. Router model?
   Answer: `gpt-5.4-mini` in real mode with heuristic fallback.
   Consequence: Simple config and low operational complexity.

5. Harmonizer and judge?
   Answer: Reuse `gpt-5.4-mini`.
   Consequence: One model family for all LLM tasks.

6. STT?
   Answer: faster-whisper small CPU, English with Indian accent.
   Consequence: Avoids GPU contention with Moshi.

7. TTS?
   Answer: Piper CPU, natural male English voice.
   Consequence: Local CPU TTS remains feasible on Mac.

8. Monthly API budget cap?
   Answer: $20/month.
   Consequence: Add call caps and keep evals modest.

9. Final demo topology?
   Answer: Mac + API.
   Consequence: Moshi/STT/TTS local; backend LLM hosted.
```

---

# 5. Next Codex Instruction

Paste or point Codex to this file, then say:

```
Read PLAN.md first.

Implement Phase 0 only.

Important:
- Do not start Phase 1.
- Do not guess Moshi wire protocol.
- Use the decisions in this file.
- Create and maintain the project-memory files: `AGENTS.md`, `PROJECT_CONTEXT.md`, `docs/PROJECT_MEMORY.md`, `docs/WORK_LOG.md`, and `docs/PHASE_STATUS.md`.
- Create docs/decisions.md from these decisions.
- Generate .env.example from the values in this file.
- Scaffold the repo exactly as required by the original implementation spec.
- Run Phase 0 acceptance tests.
- Commit with message:

phase-0: scaffold project and record model decisions

- Stop for human checkpoint.
```

---

# 6. Phase 0 Compact Implementation Plan

Title:
Phase 0 - Model Decisions + Project Scaffold

Summary:
- Record all model/hardware/demo decisions.
- Create the required skeleton repository.
- Add a bootable Spring Boot 3.x Java 21 gateway.
- Add docs, eval labels, CI, and stub-mode validation.
- Stop after Phase 0 acceptance and commit.

Key changes:
- Initialize a normal Git repository if needed.
- Create:
  - `AGENTS.md`
  - `PROJECT_CONTEXT.md`
  - `README.md`
  - `docs/PROJECT_MEMORY.md`
  - `docs/WORK_LOG.md`
  - `docs/PHASE_STATUS.md`
  - `docs/decisions.md`
  - `docs/architecture.md`
  - `docs/eval/router-labels.jsonl`
  - `gateway/` Spring Boot Maven skeleton
  - `stt-service/`
  - `tts-service/`
  - `stubs/fake-moshi/`
  - `metrics/`
  - `docker-compose.yml`
  - `docker-compose.gpu.yml`
  - `.github/workflows/ci.yml`
  - `.env.example`
- Configure gateway with:
  - Spring Boot 3.x
  - Java 21
  - Maven
  - virtual threads enabled
  - placeholder package `com.voicedemo.gateway`
- Add router label set:
  - 140 JSONL lines
  - 50 CHAT
  - 50 ASK
  - 40 ACT
  - at least 20 ambiguous examples
- Add a small schema check for the JSONL file.

Phase 0 acceptance:
- Project-memory files exist and are current.
- `AGENTS.md` instructs future LLMs to read `PLAN.md` and update memory files.
- `WORK_LOG.md` has an initial entry.
- `PHASE_STATUS.md` reflects Phase 0 state.
- `PROJECT_MEMORY.md` states current phase, completed work, blockers, and next exact step.
- `docs/decisions.md` exists with all 9 interview answers.
- `.env.example` reflects the decisions.
- Gateway boots in stub mode.
- CI runs `mvn verify`.
- Router labels validate with the schema check.
- Commit exists:
  `phase-0: scaffold project and record model decisions`
- Stop for human checkpoint.

---

# 7. Local Mac Notes

Primary local Moshi target:

```
pip install moshi_mlx
python -m moshi_mlx.local -q 4 --hf-repo "kyutai/moshiko-mlx-q4"
```

The exact server command for integration with the Java gateway must still be discovered in Phase 1 by reading the pinned Moshi source. Do not guess the protocol or endpoint details.

Development default:
- Use stub mode until Phase 1/real Moshi checkpoint.
- Do not require GPU, API key, or network model access in CI.
