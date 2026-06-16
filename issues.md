# Current Issues

Last updated: 2026-06-14 13:42 IST

Source: latest real-runtime `data/events.jsonl` and generated `data/flow-log.md`.

## Critical

### 1. Gateway can inject audio for superseded ASK jobs

Status: Open

Evidence:
- `job.dropped_stale` with `reason=superseded` appears for older correlations.
- Later events for the same superseded correlations still reach `handoff.inject.text`, `inject.start`, and `inject.end`.

Impact:
- The browser can hear an answer for an older question after the user has moved on.
- Multiple backend answers can overlap or arrive out of order.

Expected behavior:
- Once an ASK job is superseded, its answer, harmonizer response, and TTS injection must be ignored.

Proposed fix:
- Add a final active-correlation check immediately before harmonizer work and again before `inject.start`.
- Make `AskJobService` cancel/ignore in-flight futures for superseded correlations.
- Add an integration test where ASK A is superseded by ASK B, then ASK A completes late and must not inject.

### 2. Overlapping or out-of-order TTS injections

Status: Open

Evidence:
- Multiple `inject.start` events occur before older `inject.end` events.
- Later `inject.end` events appear for older correlation IDs after newer injections have already started.

Impact:
- Handoff sounds unnatural.
- Browser audio can contain stale or interleaved backend speech.

Expected behavior:
- A session should have only one active injected TTS stream.
- Starting a newer injection should cancel and end the older stream deterministically.

Proposed fix:
- Enforce single active injection per session in `OutboundMixer`.
- When a new injection starts, cancel the previous injection and emit a clear cancellation/drop event.
- Add a test that asserts one active injection per session and no late `inject.end` from stale correlations unless marked canceled.

### 3. Moshi still answers factual questions during backend handoff

Status: Open

Evidence:
- During `ASK_IN_FLIGHT`, `transcript.moshi` contains substantive answer fragments such as `student visa`, `work visa`, and `family visa`.
- During capital-of-Australia turns, Moshi continues unrelated/factual speech while the backend answer is pending.

Impact:
- The user hears Moshi answer questions that should be answered only by the backend LLM.
- The two-brain handoff does not sound natural.

Expected behavior:
- Moshi may acknowledge or stall briefly, but should not deliver factual answers while an ASK backend answer is in flight.

Proposed fix:
- Tighten `SuppressionGate` for `ASK_IN_FLIGHT` and `INJECTING`.
- Lower the allowed token threshold or classify Moshi text fragments semantically before allowing audio through.
- Consider muting Moshi audio immediately after `router.decision=ASK` except for a short acknowledgment window.

## High

### 4. Stale STT utterances are processed late

Status: Open

Evidence:
- `transcript.user` events have `endTs` values older than the event `ts` by several seconds.
- Older utterances appear after newer conversation context has already moved on.

Impact:
- Router and backend answer stale speech.
- Supersede logic is stressed and can produce wrong handoff timing.

Expected behavior:
- Very old STT utterances should be dropped or marked stale before routing.

Proposed fix:
- Add a maximum STT utterance age check using `endTs`.
- Drop or ignore utterances older than a configured threshold.
- Log `utterance.dropped_stale_stt` with age and utterance ID.

### 5. Transcript window is polluted by Moshi factual fragments and injected backend text

Status: Open

Evidence:
- Backend LLM prompts include fragmented Moshi text like `student visa`, `work visa`, and `family visa`.
- Backend-injected text is currently stored as `moshi:` transcript text, making it hard to separate real Moshi speech from backend speech.

Impact:
- Router and answer LLM receive noisy context.
- Follow-up questions can be interpreted against stale or incorrect Moshi fragments.

Expected behavior:
- Transcript should distinguish real Moshi speech from backend-injected speech.
- Suppressed Moshi fragments should not pollute the backend prompt.

Proposed fix:
- Add transcript speaker/source separation for `MOSHI`, `USER`, and `BACKEND`.
- Do not add suppressed Moshi text to the prompt window, or mark it as suppressed and filter it out.
- Update `formatTranscript` callers to include only useful context.

### 6. Router decisions can arrive after newer utterances

Status: Open

Evidence:
- Several user utterances are close together; router decisions and ASK jobs arrive out of conversational order.

Impact:
- A delayed route decision may dispatch a backend job for an older utterance.
- This contributes to superseded jobs and stale injection attempts.

Expected behavior:
- Router decisions for old utterances should be ignored if a newer user utterance already superseded them.

Proposed fix:
- Track latest user utterance per session before and after router classification.
- If classification finishes for a non-latest utterance, log and drop it before job dispatch.

### 7. Current runtime services were not running during latest inspection

Status: Operational issue

Evidence:
- `docker ps` showed no running containers.
- No process was listening on `8080`, `8081`, `8082`, or `8998`.

Impact:
- Browser cannot connect until the stack is started again.
- New logs cannot be captured until gateway/STT/TTS/Moshi are running.

Expected behavior:
- For a full real test, Moshi should listen on `8998`; gateway, STT, and TTS should be healthy on `8080`, `8081`, and `8082`.

Proposed fix:
- Start Moshi first, then run the compose overlay:

```sh
/Users/riteshrajput/.venvs/moshi-mlx/bin/python -m moshi_mlx.local_web \
  -q 4 --host 0.0.0.0 --port 8998 --no-browser

docker compose -f docker-compose.yml -f docker-compose.gpu.yml up --build
```

## Medium

### 8. Handoff timing is too slow and feels disjointed

Status: Open

Evidence:
- Router decisions take around 1.4-1.5 seconds in recent real logs.
- Injection starts several seconds after the user utterance.
- Moshi continues talking during the gap.

Impact:
- The handoff feels like two separate assistants rather than one natural response.

Expected behavior:
- Moshi should quickly acknowledge, stay out of factual content, and backend injection should arrive cleanly.

Proposed fix:
- Optimize router latency or use heuristic pre-routing for obvious factual questions.
- Start suppression earlier for likely ASK utterances.
- Add timing assertions for router decision, backend answer, harmonizer, and injection start.

### 9. Handoff report shows old rows without rich fields

Status: Expected limitation

Evidence:
- Older `BACKEND_SPOKEN` turns lack user query, confidence, reason, and LLM request/response sections.

Impact:
- Older rows are less useful for debugging.

Expected behavior:
- Only fresh runs after the rich logging patch show full details.

Proposed fix:
- Clear or archive old `data/events.jsonl` before a clean test run, or use `--tail` with fresh turns only.

### 10. Docker startup order is fragile in real overlay

Status: Open

Evidence:
- Gateway previously exited when `STT_MODE=real` and the `stt` service was missing or not yet resolvable.

Impact:
- `http://localhost:8080` may not open even though compose was invoked.

Expected behavior:
- Gateway should start only after required real sidecars are available, or compose should express all required dependencies.

Proposed fix:
- Add `stt` as a healthy dependency in `docker-compose.gpu.yml` when `STT_MODE=real`.
- Document the exact startup order.

## Checkpoint Gaps

### 11. Real suppression-rate dataset is not recorded

Status: Pending human checkpoint artifact

Impact:
- Phase 4 real-runtime suppression quality is not measured yet.

Proposed fix:
- Run a real 20-question suppression set and record suppression rate in docs/metrics.

### 12. Real metrics chart/table and demo video are still pending

Status: Pending human checkpoint artifact

Impact:
- Phase 5 and Phase 6 human checkpoint deliverables remain incomplete.

Proposed fix:
- Run a clean real demo session, generate `metrics/out-real`, update README metrics, and record the 3-minute demo video.

## Next Fix Order

1. Block superseded ASK jobs from harmonizer and TTS injection.
2. Enforce one active injection per session.
3. Drop stale STT utterances before router classification.
4. Tighten Moshi suppression during `ASK_IN_FLIGHT` and `INJECTING`.
5. Separate backend-injected transcript lines from real Moshi transcript lines.
