#!/usr/bin/env python3
"""Judge whether injected answers read like natural spoken continuations."""

from __future__ import annotations

import argparse
import json
import os
import urllib.request
from pathlib import Path


def read_samples(path: Path) -> list[dict]:
    samples: list[dict] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, 1):
            if not line.strip():
                continue
            try:
                samples.append(json.loads(line))
            except json.JSONDecodeError as exc:
                raise SystemExit(f"{path}:{line_number}: invalid JSON: {exc}") from exc
    return samples


def stub_judge(sample: dict) -> dict:
    answer = sample.get("injectedAnswer", "")
    lowered = answer.lower()
    natural = True
    reasons = []
    if "as an ai" in lowered or "language model" in lowered:
        natural = False
        reasons.append("contains assistant meta-disclaimer")
    if "\n-" in answer or "\n*" in answer:
        natural = False
        reasons.append("uses list formatting instead of spoken continuation")
    if len(answer.split()) > 45:
        natural = False
        reasons.append("too long for a short spoken injection")
    return {
        "id": sample.get("id", ""),
        "natural": natural,
        "reason": "; ".join(reasons) if reasons else "short spoken continuation",
        "mode": "stub",
    }


def real_judge(sample: dict, base_url: str, api_key: str, model: str) -> dict:
    prompt = {
        "model": model,
        "temperature": 0,
        "max_tokens": 120,
        "messages": [
            {
                "role": "system",
                "content": (
                    "Judge if the injected answer reads as a natural spoken continuation "
                    "in a live voice conversation. Return strict JSON only: "
                    '{"natural":true|false,"reason":"..."}'
                ),
            },
            {
                "role": "user",
                "content": json.dumps(
                    {
                        "transcriptWindow": sample.get("transcriptWindow", []),
                        "injectedAnswer": sample.get("injectedAnswer", ""),
                    }
                ),
            },
        ],
    }
    request = urllib.request.Request(
        base_url.rstrip("/") + "/chat/completions",
        data=json.dumps(prompt).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        payload = json.loads(response.read().decode("utf-8"))
    content = payload["choices"][0]["message"]["content"]
    judged = json.loads(content)
    return {
        "id": sample.get("id", ""),
        "natural": bool(judged["natural"]),
        "reason": judged.get("reason", ""),
        "mode": "real",
    }


def write_outputs(results: list[dict], output_dir: Path) -> dict:
    output_dir.mkdir(parents=True, exist_ok=True)
    results_path = output_dir / "judge_results.jsonl"
    with results_path.open("w", encoding="utf-8") as handle:
        for result in results:
            handle.write(json.dumps(result, sort_keys=True) + "\n")
    total = len(results)
    broken = sum(1 for result in results if not result["natural"])
    summary = {
        "sampleCount": total,
        "flowBreakCount": broken,
        "flowBreakRate": broken / total if total else 0.0,
    }
    (output_dir / "judge_summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    return summary


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("samples", nargs="?", default="metrics/fixtures/judge-samples.jsonl")
    parser.add_argument("--out", default="metrics/out")
    parser.add_argument("--mode", choices=["stub", "real"], default=os.getenv("JUDGE_MODE", "stub"))
    parser.add_argument("--base-url", default=os.getenv("LLM_BASE_URL", "https://api.openai.com/v1"))
    parser.add_argument("--model", default=os.getenv("LLM_MODEL_JUDGE", os.getenv("LLM_MODEL_ANSWER", "gpt-4o-mini")))
    args = parser.parse_args()

    samples = read_samples(Path(args.samples))
    results = []
    if args.mode == "real":
        api_key = os.getenv("LLM_API_KEY")
        if not api_key:
            raise SystemExit("LLM_API_KEY is required for --mode real")
        for sample in samples:
            results.append(real_judge(sample, args.base_url, api_key, args.model))
    else:
        results = [stub_judge(sample) for sample in samples]
    summary = write_outputs(results, Path(args.out))
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
