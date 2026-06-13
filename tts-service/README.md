# TTS Service

FastAPI sidecar for text-to-speech.

CI and normal development can use `TTS_MODE=stub` in the Java gateway, which
plays a deterministic tone. To hear spoken responses, run this sidecar and start
the gateway with `TTS_MODE=real`. On macOS it uses the system `say` command. In
Docker it uses `espeak-ng`.

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

Optional Docker/Linux voice selection:

```sh
TTS_ESPEAK_VOICE=en-us
TTS_ESPEAK_SPEED=165
```

Endpoints:

- `GET /health`
- `POST /speak` with `{"text":"Canberra is the capital of Australia."}`

`/speak` returns `audio/wav`: 24 kHz, 16-bit signed PCM, mono. Engine priority is
macOS `say` + `afconvert`, then Docker/Linux `espeak-ng`, then deterministic
placeholder tone audio only if no speech engine is available. Replace or extend
the engine later with Piper once the selected voice file is available.
