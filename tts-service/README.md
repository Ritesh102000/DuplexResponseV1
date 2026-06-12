# TTS Service

FastAPI sidecar scaffold for Phase 3.

CI and normal development can use `TTS_MODE=stub` in the Java gateway, which
plays a deterministic tone. To hear spoken responses locally on macOS, run this
sidecar and start the gateway with `TTS_MODE=real`.

```sh
/opt/homebrew/bin/python3.12 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8082
```

Use Python 3.12 for this venv on the Mac. The project machine's default
`python3` may be Python 3.14, which is too new for the pinned Pydantic build
used by this service.

Optional macOS voice selection:

```sh
TTS_VOICE=Alex uvicorn app.main:app --host 0.0.0.0 --port 8082
```

Endpoints:

- `GET /health`
- `POST /speak` with `{"text":"Canberra is the capital of Australia."}`

`/speak` returns `audio/wav`: 24 kHz, 16-bit signed PCM, mono. On macOS, it uses
the system `say` command plus `afconvert`. In Docker or on systems without those
tools, it falls back to deterministic placeholder tone audio so Phase 3 can run
without a local Piper install. Replace `synthesize_macos_say` or add a Piper
engine once the selected voice file is available.
