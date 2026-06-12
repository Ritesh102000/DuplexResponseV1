# Project Context

Current phase: Phase 1 - Moshi protocol doc + audio pass-through proxy.

Current objective:
Stop at the Phase 1 human checkpoint after documenting the Moshi protocol, adding the browser WebSocket gateway surface, fake Moshi stub, static web client, and stub-mode proxy tests.

Current status:
Phase 1 stub acceptance is complete and ready for the human checkpoint. `PLAN.md` is the canonical plan.

Next exact step:
Run the real Moshi human checkpoint when ready: start real Moshi, connect through the gateway, and confirm a full-duplex conversation through `/ws/voice` without perceptible added latency. Do not start Phase 2 until the checkpoint is accepted.

Important constraints:
- Do not guess Moshi's wire protocol.
- Stub mode is the default for CI and local development.
- Real Moshi target is local Apple Silicon MLX q4 using `kyutai/moshiko-mlx-q4`.
- Phase 1 CI acceptance uses `MOSHI_MODE=stub`; real Moshi validation remains a human checkpoint.

Validation completed:
- `python3 scripts/validate_router_labels.py docs/eval/router-labels.jsonl`
- `mvn verify`
- Phase 1 WebSocket integration tests: byte-equivalent PCM echo and reconnect after stub drop.
