# Project Context

Current phase: Phase 3 - Ask flow end-to-end.

Current objective:
Stop at the Phase 3 human checkpoint after adding the stub-mode ASK flow: delayed backend answer job, correlation IDs, supersede/stale policy, harmonizer, TTS injection, outbound mixer suppression, and Phase 3 tests.

Current status:
Phase 3 automated acceptance is complete and ready for the human checkpoint. `PLAN.md` is the canonical plan. The human request to start Phase 3 is treated as approval of the Phase 2 checkpoint. For local spoken backend answers, run `tts-service` on macOS from a Python 3.12 venv and start the gateway with `TTS_MODE=real`; `TTS_MODE=stub` intentionally plays a tone.

Recent real-Moshi probe:
Local Moshi MLX exists at `/Users/riteshrajput/.venvs/moshi-mlx/bin/python` with `moshi_mlx 0.3.0`. The server starts with `python -m moshi_mlx.local_web -q 4 --host 127.0.0.1 --port 8998 --no-browser`, loads cached `kyutai/moshiko-mlx-q4`, and accepts `/api/chat` with handshake `00`. `RealMoshiClient` bridges raw 24 kHz PCM from the browser to Ogg/Opus for Moshi and decodes Moshi Ogg/Opus back to raw PCM for the browser. Moshi's `sphn` writer emits OpusHead input rate `48000`, so the Java decoder must decode at 48 kHz and downsample to the browser's 24 kHz PCM. The static UI has AudioWorklet mic capture, queued PCM playback, browser audio unlock, a speaker test, and output peak logging.

Next exact step:
Run the Phase 3 human checkpoint: start real Moshi plus the gateway, ask a factual question through the project browser, confirm Moshi acknowledges quickly, and confirm the harmonized injected answer is spoken after the backend delay.

Important constraints:
- Do not guess Moshi's wire protocol.
- Stub mode is the default for CI and local development.
- Real Moshi target is local Apple Silicon MLX q4 using `kyutai/moshiko-mlx-q4`.
- Phase 3 CI acceptance uses stub Moshi/STT/TTS/LLM paths; real Moshi, Piper voice quality, and hosted LLM behavior remain human checkpoint work.

Validation completed:
- `python3 scripts/validate_router_labels.py docs/eval/router-labels.jsonl`
- `python3 scripts/router_eval.py docs/eval/router-labels.jsonl`
- `mvn verify`
- `mvn -pl gateway -Dtest=OggOpusCodecTests test`
- Phase 1 WebSocket integration tests: byte-equivalent PCM echo and reconnect after stub drop.
- Real Moshi smoke test: started `moshi_mlx.local_web`, ran gateway with `MOSHI_MODE=real`, sent 12 PCM frames through `/ws/voice`, and received a binary PCM response without Moshi Ogg errors.
- Real Moshi spoken-phrase probe before the decoder-rate fix: sent a generated 24 kHz mono phrase through `/ws/voice`; received 26 text/control messages and 256 binary PCM chunks, but Java decoded Moshi `sphn` Opus badly when forced to 24 kHz.
- Moshi `sphn` fixture validation after the decoder-rate fix: a Moshi-style Ogg/Opus fixture decodes to a 3,840-byte browser PCM frame with healthy nonzero signal.
- Real Moshi spoken-phrase probe after the decoder-rate fix: received 26 text/control messages and 216 binary PCM chunks with max decoded peak about 0.5909.
- Phase 2 router and transcript tests: heuristic router label-set coverage, ambiguous routing checks, transcript ordering/capacity, and browser transcript route-decision integration.
- Phase 3 ASK-flow tests: exhaustive session state table, WebSocket ASK event sequence, injected TTS audio, Moshi-audio suppression during injection, stale-drop policy, and supersede policy.
- Phase 3 validation: `mvn -pl gateway -Dtest=SessionStateMachineTests,Phase3AskFlowIntegrationTests test`, `mvn -pl gateway verify`, router-label validation, Python compile checks, JS syntax checks, and `git diff --check`.
- Local TTS probe: macOS `say` + `afconvert` produced 24 kHz, 16-bit mono WAV for backend answer speech.
- TTS service setup probe: default `python3` is 3.14 and failed pinned Pydantic/PyO3 install; `/opt/homebrew/bin/python3.12` venv installed successfully and `/speak` returned valid WAV.
