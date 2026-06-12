# Metrics

Phase 5 analyzes gateway JSONL event logs and judges whether injected answers
read as natural spoken continuations.

## Fixture Analysis

```sh
python3 metrics/analyze.py metrics/fixtures/events.jsonl --out metrics/out
python3 metrics/judge_flow.py metrics/fixtures/judge-samples.jsonl --out metrics/out --mode stub
```

Generated files:

- `latency_chart.png`: perceived-vs-true ASK latency chart.
- `summary.json`: headline latency, suppression, barge-in, and stale-drop metrics.
- `ask_latencies.csv`: per-ASK latency rows.
- `router_confusion_matrix.csv`: expected-vs-predicted router labels when `expectedLabel` is present.
- `judge_results.jsonl`: per-sample naturalness decisions.
- `judge_summary.json`: flow-break count and rate.

## Real Logs

```sh
python3 metrics/analyze.py data/events.jsonl --out metrics/out-real
```

`moshi.first_audio` is only logged by gateway builds from Phase 5 onward. Older
logs can still produce true latency and suppression summaries, but perceived
latency will be missing.

## Real Judge

```sh
python3 metrics/judge_flow.py metrics/fixtures/judge-samples.jsonl --out metrics/out --mode real
```

Real judge mode uses `LLM_API_KEY`, `LLM_BASE_URL`, and `LLM_MODEL_JUDGE`
or `LLM_MODEL_ANSWER`.
