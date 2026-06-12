# STT Service

FastAPI sidecar scaffold for Phase 2.

CI and normal development use `STT_MODE=stub` in the Java gateway. This service
exposes a lightweight health endpoint and a stub utterance endpoint while the
real Silero VAD + faster-whisper runtime remains behind the sidecar boundary.

```sh
/opt/homebrew/bin/python3.12 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8081
```

Endpoints:

- `GET /health`
- `POST /stub/utterance` with `{"text":"what is the capital of australia"}`
- `POST /transcribe` placeholder for real audio transcription
