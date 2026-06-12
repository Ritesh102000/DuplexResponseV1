# Two-Tier Voice Assistant Demo

This project is a real-time voice assistant demo with Moshi handling live conversational audio and a backend LLM handling slower reasoning answers.

`PLAN.md` is the canonical implementation plan. Work proceeds phase by phase and stops at each human checkpoint.

## Current Phase

Phase 3 - Ask flow end-to-end. Automated stub acceptance is complete; human real-runtime checkpoint is next.

## Development Defaults

- Stub mode by default: no GPU, API key, or model-provider network required for CI.
- Gateway: Spring Boot 3.x, Java 21, Maven.
- Real local Moshi target: Apple Silicon MLX q4 with `kyutai/moshiko-mlx-q4`.

## Phase 0 Checks

```sh
mvn verify
python3 scripts/validate_router_labels.py docs/eval/router-labels.jsonl
```

## Phase 1 Checks

The gateway exposes `/ws/voice` in stub mode. The Phase 1 integration tests connect to that endpoint, send one 80 ms PCM frame, and assert the echoed audio is byte-equivalent.

```sh
mvn verify
python3 scripts/validate_router_labels.py docs/eval/router-labels.jsonl
```

To try the browser shell:

```sh
java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar
```

Then open `http://localhost:8080` and use **Connect** followed by **Send Test Frame**.

To talk through real local Moshi from this project, start Moshi first:

```sh
/Users/riteshrajput/.venvs/moshi-mlx/bin/python -m moshi_mlx.local_web \
  -q 4 --host 127.0.0.1 --port 8998 --no-browser
```

Then start the gateway in real Moshi mode:

```sh
MOSHI_MODE=real \
MOSHI_WS_URL=ws://127.0.0.1:8998/api/chat \
STT_MODE=stub \
LLM_MODE=stub \
TTS_MODE=stub \
java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar
```

Open `http://localhost:8080`, click **Test Speaker** to confirm browser output, then click **Connect** and **Start Mic**. The gateway keeps the browser side as raw 24 kHz PCM and bridges to Moshi's Ogg/Opus protocol internally.

If you see Moshi text in the debug panel but cannot hear audio, check the binary audio lines. `peak` is the raw decoded Moshi PCM level and `out` is the browser output level; values near `0.000` mean Moshi is returning silence for that chunk.

## Phase 2 Checks

Phase 2 adds transcript buffering and router decisions.

```sh
python3 scripts/validate_router_labels.py docs/eval/router-labels.jsonl
python3 scripts/router_eval.py docs/eval/router-labels.jsonl
mvn verify
```

To try the browser router path:

```sh
java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar
```

Open `http://localhost:8080`, click **Connect**, then click **Send Utterance**.
The debug panel should show `transcript.user` and `router.decision` messages.

The STT sidecar scaffold can be started separately:

```sh
cd stt-service
/opt/homebrew/bin/python3.12 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8081
```

## Phase 3 Checks

Phase 3 adds the ASK job flow: correlation IDs, per-session supersede, stale-drop policy, delayed backend answer, harmonizer, TTS injection, and outbound mixer suppression while injected audio plays. `TTS_MODE=stub` plays a tone; use `TTS_MODE=real` with the TTS sidecar to hear spoken local responses.

```sh
mvn -pl gateway -Dtest=SessionStateMachineTests,Phase3AskFlowIntegrationTests test
mvn -pl gateway verify
python3 scripts/validate_router_labels.py docs/eval/router-labels.jsonl
node --check gateway/src/main/resources/static/app.js
node --check gateway/src/main/resources/static/mic-capture-worklet.js
```

To try the stub ASK path in the browser:

```sh
mvn -pl gateway package
java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar
```

Open `http://localhost:8080`, click **Test Speaker**, click **Connect**, enter `what is the capital of australia`, then click **Send Utterance**. The debug panel should show `router.decision` with `ASK`, followed by `inject.start`, injected PCM audio, and `inject.end`.

The TTS sidecar contract can be started separately:

```sh
cd tts-service
/opt/homebrew/bin/python3.12 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8082
```

Then start the gateway with `TTS_MODE=real`. On macOS, the sidecar uses the
system `say` voice and returns 24 kHz mono WAV audio to the gateway.
