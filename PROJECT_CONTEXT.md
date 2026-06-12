# Project Context

Current phase: Phase 0 - Model Decisions + Project Scaffold.

Current objective:
Create the project-memory files, record model/demo decisions, scaffold a bootable Spring Boot Java 21 gateway, add documentation and router-label fixtures, run stub-mode validation, commit, and stop for the human checkpoint.

Current status:
Phase 0 scaffold is complete and ready for the human checkpoint. `PLAN.md` is the canonical plan. The repository has been initialized with git.

Next exact step:
Review Phase 0 output and complete the human checkpoint. Do not start Phase 1 until the checkpoint is accepted.

Important constraints:
- Do not start Phase 1.
- Do not guess Moshi's wire protocol.
- Stub mode is the default for CI and local development.
- Real Moshi target is local Apple Silicon MLX q4 using `kyutai/moshiko-mlx-q4`.

Validation completed:
- `python3 scripts/validate_router_labels.py docs/eval/router-labels.jsonl`
- `mvn verify`
- Packaged gateway boot check with `java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar --server.port=0`
