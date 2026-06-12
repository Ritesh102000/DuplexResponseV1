# Two-Tier Voice Assistant Demo

This project is a real-time voice assistant demo with Moshi handling live conversational audio and a backend LLM handling slower reasoning answers.

`PLAN.md` is the canonical implementation plan. Work proceeds phase by phase and stops at each human checkpoint.

## Current Phase

Phase 0 - Model Decisions + Project Scaffold.

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
