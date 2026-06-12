# Project Context

Current phase: Phase 2 - Transcripts + router.

Current objective:
Stop at the Phase 2 human checkpoint after adding STT boundaries, transcript memory, router classification, ACT canned responses, route-decision events, event logging, and offline router evaluation.

Current status:
Phase 2 automated acceptance is complete and ready for the human checkpoint. `PLAN.md` is the canonical plan.

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
- Phase 1 WebSocket integration tests: byte-equivalent PCM echo and reconnect after stub drop.
- Phase 2 router and transcript tests: heuristic router label-set coverage, ambiguous routing checks, transcript ordering/capacity, and browser transcript route-decision integration.
