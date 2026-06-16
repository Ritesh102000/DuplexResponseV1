#!/usr/bin/env python3
"""Write a timing-only report from gateway JSONL events."""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from typing import Any


def read_events(path: Path) -> list[dict[str, Any]]:
    events: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, 1):
            if not line.strip():
                continue
            try:
                events.append(json.loads(line))
            except json.JSONDecodeError as exc:
                raise SystemExit(f"{path}:{line_number}: invalid JSON: {exc}") from exc
    return events


def event_key(event: dict[str, Any]) -> tuple[str, str]:
    return str(event.get("sessionId", "")), str(event.get("utteranceId", ""))


def ts(event: dict[str, Any] | None) -> int | None:
    if not event or event.get("ts") is None:
        return None
    try:
        return int(event["ts"])
    except (TypeError, ValueError):
        return None


def fmt_ts(value: int | None) -> str:
    if value is None:
        return "-"
    return datetime.fromtimestamp(value / 1000).strftime("%H:%M:%S.%f")[:-3]


def ms_value(value: Any) -> int | None:
    if value is None:
        return None
    try:
        return max(0, int(value))
    except (TypeError, ValueError):
        return None


def duration(value: Any) -> str:
    milliseconds = ms_value(value)
    if milliseconds is None:
        return "-"
    if milliseconds >= 1000:
        return f"{milliseconds / 1000:.2f}s"
    return f"{milliseconds}ms"


def short_id(value: str) -> str:
    return value[-8:] if len(value) > 8 else value


def first_event(events: list[dict[str, Any]], name: str) -> dict[str, Any] | None:
    return next((event for event in events if event.get("event") == name), None)


def first_tts(events: list[dict[str, Any]], name: str, phase: str) -> dict[str, Any] | None:
    return next(
        (
            event for event in events
            if event.get("event") == name and event.get("phase") == phase
        ),
        None,
    )


def delta(start: dict[str, Any] | None, end: dict[str, Any] | None) -> int | None:
    start_ts = ts(start)
    end_ts = ts(end)
    if start_ts is None or end_ts is None:
        return None
    return max(0, end_ts - start_ts)


def latency(event: dict[str, Any] | None) -> int | None:
    if not event or event.get("latencyMs") is None:
        return None
    try:
        return max(0, int(event["latencyMs"]))
    except (TypeError, ValueError):
        return None


def stt_lookup(events: list[dict[str, Any]]) -> dict[tuple[str, int], int]:
    lookup: dict[tuple[str, int], int] = {}
    for event in events:
        if event.get("event") != "stt.transcribe.response":
            continue
        session_id = str(event.get("sessionId", ""))
        try:
            end_ts = int(event.get("endTs"))
        except (TypeError, ValueError):
            continue
        if latency(event) is not None:
            lookup[(session_id, end_ts)] = latency(event) or 0
    return lookup


def group_turns(events: list[dict[str, Any]]) -> list[tuple[tuple[str, str], list[dict[str, Any]]]]:
    correlation_to_key: dict[str, tuple[str, str]] = {}
    for event in events:
        correlation_id = event.get("correlationId")
        utterance_id = event.get("utteranceId")
        if correlation_id and utterance_id:
            correlation_to_key[str(correlation_id)] = event_key(event)

    turns: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for event in events:
        key = event_key(event)
        if key[1]:
            turns[key].append(event)
            continue
        correlation_id = event.get("correlationId")
        if correlation_id and str(correlation_id) in correlation_to_key:
            turns[correlation_to_key[str(correlation_id)]].append(event)

    return sorted(
        turns.items(),
        key=lambda item: max(int(event.get("ts", 0)) for event in item[1]),
    )


def row_for_turn(
        key: tuple[str, str],
        turn_events: list[dict[str, Any]],
        stt_by_end_ts: dict[tuple[str, int], int]) -> dict[str, Any]:
    session_id, utterance_id = key
    events = sorted(turn_events, key=lambda event: int(event.get("ts", 0)))
    user = first_event(events, "transcript.user") or first_event(events, "utterance.end")
    decision = first_event(events, "router.decision")
    sanitizer = first_event(events, "fast.prompt.sanitized")
    fast_response = first_event(events, "fast.reply.response")
    answer_response = first_event(events, "llm.answer.response") or first_event(events, "llm.answer.error")
    harmonizer_response = first_event(events, "llm.harmonizer.response") or first_event(events, "llm.harmonizer.error")
    fast_start = first_event(events, "fast.reply.start")
    fast_end = first_event(events, "fast.reply.end")
    backend_start = first_event(events, "backend.inject.start") or first_event(events, "inject.start")
    backend_end = first_event(events, "backend.inject.end") or first_event(events, "inject.end")
    fast_tts_end = first_tts(events, "tts.end", "fast")
    backend_tts_end = first_tts(events, "tts.end", "backend")

    stt_ms: int | None = None
    if user and user.get("endTs") is not None:
        try:
            stt_ms = stt_by_end_ts.get((session_id, int(user["endTs"])))
        except (TypeError, ValueError):
            stt_ms = None

    return {
        "time": fmt_ts(ts(user) or ts(events[0])),
        "session": session_id,
        "utterance": utterance_id,
        "label": str(decision.get("label", "-")) if decision else "-",
        "stt": stt_ms,
        "router": latency(decision) if decision else delta(user, decision),
        "sanitizer": latency(sanitizer),
        "front_llm": latency(fast_response),
        "back_llm": latency(answer_response),
        "harmonizer": latency(harmonizer_response),
        "tts_fast_total": latency(fast_tts_end) if fast_tts_end else delta(fast_start, fast_end),
        "tts_back_total": latency(backend_tts_end) if backend_tts_end else delta(backend_start, backend_end),
    }


def slowest_step(row: dict[str, Any]) -> str:
    steps = [
        ("STT", "stt"),
        ("router", "router"),
        ("sanitizer", "sanitizer"),
        ("front LLM", "front_llm"),
        ("front TTS", "tts_fast_total"),
        ("back LLM", "back_llm"),
        ("harmonizer", "harmonizer"),
        ("back TTS", "tts_back_total"),
    ]
    timings = [(label, ms_value(row[key])) for label, key in steps]
    timings = [(label, value) for label, value in timings if value is not None]
    if not timings:
        return "-"
    label, value = max(timings, key=lambda item: item[1])
    return f"slowest: {label} {duration(value)}"


def timing_cell(label: str, value: Any) -> str:
    return f"{label}: {duration(value)}"


def markdown_table(rows: list[dict[str, Any]]) -> list[str]:
    headers = [
        ("index", "#"),
        ("time", "time"),
        ("label", "route"),
        ("stt", "STT"),
        ("router", "router"),
        ("sanitizer", "sanitizer"),
        ("front_llm", "front LLM"),
        ("tts_fast_total", "front TTS"),
        ("back_llm", "back LLM"),
        ("harmonizer", "harmonizer"),
        ("tts_back_total", "back TTS"),
        ("slowest", "slowest"),
        ("session", "session"),
        ("utterance", "utterance"),
    ]
    timing_keys = {
        "stt": "STT",
        "router": "router",
        "sanitizer": "sanitizer",
        "front_llm": "front LLM",
        "tts_fast_total": "front TTS",
        "back_llm": "back LLM",
        "harmonizer": "harmonizer",
        "tts_back_total": "back TTS",
    }
    lines = [
        "| " + " | ".join(label for _, label in headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |",
    ]
    for index, row in enumerate(rows, 1):
        display = {
            **row,
            "index": str(index),
            "session": short_id(row["session"]),
            "utterance": short_id(row["utterance"]),
            "slowest": slowest_step(row),
        }
        cells = []
        for key, _ in headers:
            if key in timing_keys:
                cells.append(timing_cell(timing_keys[key], display[key]))
            else:
                cells.append(str(display[key]))
        lines.append("| " + " | ".join(cells) + " |")
    return lines


def write_timing_log(
        events: list[dict[str, Any]],
        input_path: Path,
        output: Path,
        tail: int,
        session_id: str | None) -> None:
    if session_id:
        events = [event for event in events if event.get("sessionId") == session_id]

    stt_by_end_ts = stt_lookup(events)
    turns = group_turns(events)
    if tail > 0:
        turns = turns[-tail:]

    rows = [row_for_turn(key, turn_events, stt_by_end_ts) for key, turn_events in turns]
    lines = [
        "# Voice Timing Log",
        "",
        f"Source: `{input_path}`",
        f"Generated: `{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}`",
        f"Events read: `{len(events)}`",
        f"Turns shown: `{len(rows)}`",
        "",
        "Timing is shown as `ms` under one second and `s` above one second. Example: `2203 ms` is shown as `2.20s`.",
        "`-` means that step did not run in this mode or the older log did not include that timing event.",
        "This report intentionally omits conversation text.",
        "",
    ]
    if rows:
        lines.extend(markdown_table(rows))
    else:
        lines.append("No turn events found.")
    lines.append("")

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a timing-only report from gateway JSONL events.")
    parser.add_argument("input", nargs="?", default="data/events.jsonl", help="gateway JSONL event file")
    parser.add_argument("--out", default="data/timing-log.md", help="Markdown file to write")
    parser.add_argument("--tail", type=int, default=20, help="number of recent turns to include; 0 means all")
    parser.add_argument("--session", help="only include one sessionId")
    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.exists():
        raise SystemExit(f"{input_path} does not exist yet; run the gateway and make one turn first")
    write_timing_log(read_events(input_path), input_path, Path(args.out), args.tail, args.session)
    print(f"wrote {args.out}")


if __name__ == "__main__":
    main()
