# TTS Service

FastAPI sidecar scaffold for Phase 3.

CI and normal development use `TTS_MODE=stub` in the Java gateway. This service
exposes the real sidecar contract while the concrete Piper voice/model can be
installed later without changing gateway code.

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8082
```

Endpoints:

- `GET /health`
- `POST /speak` with `{"text":"Canberra is the capital of Australia."}`

`/speak` returns `audio/wav`: 24 kHz, 16-bit signed PCM, mono. The current
implementation generates deterministic placeholder audio so Phase 3 can run
without a local Piper install. Replace `synthesize_stub_pcm` in `app/main.py`
with Piper invocation once the selected voice file is available.
