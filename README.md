# Two-Tier Voice Assistant Demo

This project is a real-time voice assistant demo with Moshi handling live conversational audio and a backend LLM handling slower reasoning answers.

`PLAN.md` is the canonical implementation plan. Work proceeds phase by phase and stops at each human checkpoint.

## Current Phase

Phase 2 - Transcripts + Router.

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
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8081
```
