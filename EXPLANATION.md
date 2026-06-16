# Explanation

This project is a two-tier voice assistant demo.

The current main runtime is Qwen-first:

```text
Browser mic
-> gateway
-> STT sidecar
-> router
-> fast local Qwen reply for conversation
-> backend LLM for factual answers
-> TTS sidecar
-> browser speaker
```

## Core Idea

There are two model roles:

- Fast layer: local Qwen through Ollama. It keeps the conversation moving.
- Backend layer: hosted OpenAI-compatible LLM. It owns factual answers.

For factual `ASK` turns, Qwen should not answer the fact. It should speak a short conversational holding reply while the backend answer is generated and injected later.

## Current Runtime

- Gateway: Spring Boot Java service in `gateway/`.
- STT: FastAPI service in `stt-service/`, using faster-whisper in real mode.
- TTS: FastAPI service in `tts-service/`, using macOS `say` locally or `espeak-ng` in Docker.
- Fast LLM: Ollama on `http://127.0.0.1:11434/v1`.
- Backend LLM: configured by `.env`.

## Important Files

- `PLAN.md`: canonical project plan and phase history.
- `RUN.md`: exact local run steps.
- `AGENTS.md`: rules future coding agents must follow.
- `PROJECT_CONTEXT.md`: short current-state handoff.
- `docs/PROJECT_MEMORY.md`: living technical memory.
- `docs/WORK_LOG.md`: chronological work history.
- `docs/PHASE_STATUS.md`: phase and checkpoint status.
- `issues.md`: current known runtime issues.

## Key Gateway Areas

- Routing: `gateway/src/main/java/com/voicedemo/gateway/router/`
- Conversation flow: `gateway/src/main/java/com/voicedemo/gateway/session/`
- Backend jobs: `gateway/src/main/java/com/voicedemo/gateway/jobs/`
- LLM clients and prompts: `gateway/src/main/java/com/voicedemo/gateway/llm/`
- STT/TTS/audio flow: `gateway/src/main/java/com/voicedemo/gateway/speech/`
- Browser WebSocket: `gateway/src/main/java/com/voicedemo/gateway/ws/`

## Safety Boundary

`AskPendingPromptSanitizer` prevents raw factual questions from being sent to Qwen during `ASK_PENDING`.

The next missing safety step is a verifier before TTS. It should block factual-looking Qwen `ASK_PENDING` replies and replace them with a safe spoken fallback.

## Local-Only Files

Do not commit:

- `.env`
- `.env.*`
- `data/`
- `gateway/data/`
- `fast-layer-finetune/`
- virtualenvs, caches, and build outputs

The fine-tuning workspace and runtime logs are useful locally, but they are not part of the pushed project state.
