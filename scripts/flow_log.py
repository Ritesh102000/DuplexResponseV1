#!/usr/bin/env python3
"""Write a compact human-readable flow log from gateway JSONL events."""

from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from typing import Any


KEY_EVENTS = {
    "utterance.end",
    "router.decision",
    "job.dispatched",
    "job.completed",
    "job.dropped_stale",
    "moshi.first_audio",
    "suppression.faded",
    "barge_in",
    "inject.start",
    "inject.end",
}


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


def fmt_ts(ts: Any) -> str:
    if ts is None:
        return "-"
    try:
        return datetime.fromtimestamp(int(ts) / 1000).strftime("%H:%M:%S.%f")[:-3]
    except (TypeError, ValueError, OSError):
        return str(ts)


def verdict(events: list[dict[str, Any]]) -> str:
    names = {event.get("event") for event in events}
    decisions = [event for event in events if event.get("event") == "router.decision"]
    label = decisions[-1].get("label") if decisions else None

    if label == "CHAT":
        return "MOSHI_ONLY"
    if label == "ACT":
        return "ACT_STUB"
    if label == "ASK":
        if "inject.end" in names:
            return "BACKEND_SPOKEN"
        if "inject.start" in names:
            return "BACKEND_INJECTION_STARTED_NO_END"
        if "job.completed" in names:
            return "BACKEND_ANSWER_READY_NO_TTS"
        if "job.dispatched" in names:
            return "BACKEND_JOB_DISPATCHED_PENDING"
        return "ASK_ROUTED_NO_JOB"
    return "NO_ROUTER_DECISION"


def detail_line(event: dict[str, Any]) -> str:
    name = event.get("event", "?")
    parts = [fmt_ts(event.get("ts")), str(name)]
    if name == "router.decision":
        parts.append(f"label={event.get('label', '-')}")
        if event.get("correlationId"):
            parts.append(f"correlationId={event.get('correlationId')}")
    elif name == "job.completed":
        parts.append(f"latencyMs={event.get('latencyMs', '-')}")
        if event.get("fallback"):
            parts.append(f"fallback={event.get('fallback')}")
        if event.get("reason"):
            parts.append(f"reason={event.get('reason')}")
    elif name == "job.dropped_stale":
        parts.append(f"reason={event.get('reason', '-')}")
    elif name == "moshi.first_audio":
        parts.append(f"correlationId={event.get('correlationId', '-')}")
    elif name == "suppression.faded":
        parts.append(f"tokenCount={event.get('tokenCount', '-')}")
    elif name in {"inject.start", "inject.end", "barge_in"}:
        parts.append(f"correlationId={event.get('correlationId', '-')}")
    return " | ".join(parts)


def write_flow_log(
        events: list[dict[str, Any]],
        input_path: Path,
        output: Path,
        tail: int,
        session_id: str | None) -> None:
    if session_id:
        events = [event for event in events if event.get("sessionId") == session_id]

    turns: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    loose_events: list[dict[str, Any]] = []
    for event in events:
        name = event.get("event")
        if name not in KEY_EVENTS:
            continue
        key = event_key(event)
        if key[1]:
            turns[key].append(event)
        else:
            loose_events.append(event)

    ordered_turns = sorted(turns.items(), key=lambda item: max(int(event.get("ts", 0)) for event in item[1]))
    recent_turns = ordered_turns[-tail:] if tail > 0 else ordered_turns
    verdicts = Counter(verdict(turn_events) for _, turn_events in recent_turns)

    lines = [
        "# Voice Flow Log",
        "",
        f"Source: `{input_path}`",
        f"Generated: `{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}`",
        f"Events read: `{len(events)}`",
        f"Turns shown: `{len(recent_turns)}`",
        "",
        "Important: with `STT_MODE=stub`, microphone speech goes to Moshi but does not create router/backend LLM handoff. Use the browser text box plus `Send Utterance` to test the backend handoff until real STT is implemented.",
        "",
        "## Summary",
        "",
    ]

    if verdicts:
        for name, count in verdicts.most_common():
            lines.append(f"- `{name}`: {count}")
    else:
        lines.append("- No turn events found.")

    lines.extend(["", "## Recent Turns", ""])
    for (turn_session, utterance_id), turn_events in recent_turns:
        sorted_events = sorted(turn_events, key=lambda event: int(event.get("ts", 0)))
        decision = next((event for event in sorted_events if event.get("event") == "router.decision"), {})
        label = decision.get("label", "-")
        result = verdict(sorted_events)
        lines.append(f"### {fmt_ts(sorted_events[0].get('ts'))} `{result}`")
        lines.append("")
        lines.append(f"- session: `{turn_session}`")
        lines.append(f"- utterance: `{utterance_id}`")
        lines.append(f"- router label: `{label}`")
        if decision.get("correlationId"):
            lines.append(f"- correlation: `{decision.get('correlationId')}`")
        lines.append("- timeline:")
        for event in sorted_events:
            lines.append(f"  - {detail_line(event)}")
        lines.append("")

    recent_loose = sorted(loose_events, key=lambda event: int(event.get("ts", 0)))[-tail:]
    if recent_loose:
        lines.extend(["## Recent Session Events", ""])
        for event in recent_loose:
            lines.append(f"- `{event.get('sessionId', '')}`: {detail_line(event)}")
        lines.append("")

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8")

def main() -> None:
    parser = argparse.ArgumentParser(description="Create a compact flow log from gateway JSONL events.")
    parser.add_argument("input", nargs="?", default="data/events.jsonl", help="gateway JSONL event file")
    parser.add_argument("--out", default="data/flow-log.md", help="Markdown file to write")
    parser.add_argument("--tail", type=int, default=20, help="number of recent turns to include; 0 means all")
    parser.add_argument("--session", help="only include one sessionId")
    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.exists():
        raise SystemExit(f"{input_path} does not exist yet; run the gateway and make one turn first")
    events = read_events(input_path)
    output = Path(args.out)
    write_flow_log(events, input_path, output, args.tail, args.session)
    print(f"wrote {output}")


if __name__ == "__main__":
    main()
