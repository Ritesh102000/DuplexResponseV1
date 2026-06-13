# STT Service

FastAPI sidecar for speech-to-text.

The Java gateway does lightweight PCM endpointing and sends utterance-sized WAV
chunks to this service. The service transcribes them with `faster-whisper`.
CI can still use `STT_MODE=stub` in the gateway when no model runtime is needed.

```sh
/opt/homebrew/bin/python3.12 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8081
```

Useful runtime variables:

```sh
export STT_WHISPER_MODEL=small
export STT_DEVICE=cpu
export STT_COMPUTE_TYPE=int8
export STT_LANGUAGE=en
```

The first real transcription downloads/loads the configured Whisper model and
can take noticeably longer than later requests.

Endpoints:

- `GET /health`
- `POST /stub/utterance` with `{"text":"what is the capital of australia"}`
- `POST /transcribe?endTs=...` with `Content-Type: audio/wav`; returns a list of
  `{ "text": "...", "endTs": ... }`
