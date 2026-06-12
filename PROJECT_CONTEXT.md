# Project Context

Current phase: Phase 2 - Transcripts + router.

Current objective:
Stop at the Phase 2 human checkpoint after adding STT boundaries, transcript memory, router classification, ACT canned responses, route-decision events, event logging, and offline router evaluation.

Current status:
Phase 2 automated acceptance is complete and ready for the human checkpoint. `PLAN.md` is the canonical plan.

Recent real-Moshi probe:
Local Moshi MLX exists at `/Users/riteshrajput/.venvs/moshi-mlx/bin/python` with `moshi_mlx 0.3.0`. The server starts with `python -m moshi_mlx.local_web -q 4 --host 127.0.0.1 --port 8998 --no-browser`, loads cached `kyutai/moshiko-mlx-q4`, and accepts `/api/chat` with handshake `00`. `RealMoshiClient` now bridges raw 24 kHz PCM from the browser to Ogg/Opus for Moshi and decodes Moshi Ogg/Opus back to raw PCM for the browser. The static UI has AudioWorklet mic capture, queued PCM playback, browser audio unlock, a speaker test, output peak logging, and 6x clipped output gain. A local spoken-phrase probe through `/ws/voice` returned Moshi text plus 256 decoded PCM chunks; raw peak was low at about 0.039, so browser gain is needed for audibility.

Next exact step:
Run the Phase 2 human checkpoint: execute the router eval with the real hosted OpenAI-compatible LLM key/model, record the accuracy/confusion matrix, and confirm route-decision behavior in the browser. Do not start Phase 3 until the checkpoint is accepted.

Important constraints:
- Do not guess Moshi's wire protocol.
- Stub mode is the default for CI and local development.
- Real Moshi target is local Apple Silicon MLX q4 using `kyutai/moshiko-mlx-q4`.
- Phase 2 CI acceptance uses stub Moshi/STT/LLM paths; real LLM validation remains a human checkpoint.

Validation completed:
- `python3 scripts/validate_router_labels.py docs/eval/router-labels.jsonl`
- `python3 scripts/router_eval.py docs/eval/router-labels.jsonl`
- `mvn verify`
- `mvn -pl gateway -Dtest=OggOpusCodecTests test`
- Phase 1 WebSocket integration tests: byte-equivalent PCM echo and reconnect after stub drop.
- Real Moshi smoke test: started `moshi_mlx.local_web`, ran gateway with `MOSHI_MODE=real`, sent 12 PCM frames through `/ws/voice`, and received a binary PCM response without Moshi Ogg errors.
- Real Moshi spoken-phrase probe: sent a generated 24 kHz mono phrase through `/ws/voice`; received 26 text/control messages and 256 binary PCM chunks with max raw peak about 0.039.
- Phase 2 router and transcript tests: heuristic router label-set coverage, ambiguous routing checks, transcript ordering/capacity, and browser transcript route-decision integration.
