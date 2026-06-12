# Agent Operating Rules

This repository is driven by `PLAN.md`. Before writing code, every agent must read:

- `PLAN.md`
- `PROJECT_CONTEXT.md`
- `docs/PROJECT_MEMORY.md`
- `docs/WORK_LOG.md`
- `docs/PHASE_STATUS.md`
- `docs/decisions.md`

Work strictly phase by phase. Do not start the next phase until the current phase's human checkpoint is complete or the human explicitly requests the next phase.

Before stopping, committing, reporting a blocker, or handing off, update:

- `PROJECT_CONTEXT.md`
- `docs/PROJECT_MEMORY.md`
- `docs/WORK_LOG.md`
- `docs/PHASE_STATUS.md`

Do not guess Moshi's wire protocol. Phase 1 must extract it from pinned Moshi source before any real Moshi client implementation.
