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
    "transcript.user",
    "transcript.moshi",
    "transcript.fast",
    "transcript.backend",
    "router.input",
    "utterance.end",
    "utterance.dropped_stale_stt",
    "router.decision",
    "router.dropped_stale",
    "ask.pending.start",
    "ask.pending.superseded",
    "fast.reply.request",
    "fast.reply.response",
    "fast.reply.start",
    "fast.reply.end",
    "fast.reply.canceled",
    "job.dispatched",
    "job.completed",
    "job.dropped_stale",
    "llm.answer.request",
    "llm.answer.response",
    "llm.answer.error",
    "llm.harmonizer.request",
    "llm.harmonizer.response",
    "llm.harmonizer.error",
    "moshi.first_audio",
    "suppression.faded",
    "barge_in",
    "handoff.inject.text",
    "backend.answer.ready",
    "backend.answer.queued",
    "backend.inject.start",
    "backend.inject.end",
    "backend.inject.canceled",
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


def short(value: Any, limit: int = 220) -> str:
    text = "" if value is None else str(value)
    text = " ".join(text.split())
    if len(text) <= limit:
        return text
    return text[: limit - 1].rstrip() + "..."


def full_text(value: Any) -> str:
    return "" if value is None else str(value).strip()


def first_event(events: list[dict[str, Any]], name: str) -> dict[str, Any] | None:
    return next((event for event in events if event.get("event") == name), None)


def events_named(events: list[dict[str, Any]], name: str) -> list[dict[str, Any]]:
    return [event for event in events if event.get("event") == name]


def delta_line(label: str, event: dict[str, Any] | None, base_ts: int | None) -> str | None:
    if not event or base_ts is None or "ts" not in event:
        return None
    return f"- {label}: `{max(0, int(event['ts']) - base_ts)} ms`"


def verdict(events: list[dict[str, Any]]) -> str:
    names = {event.get("event") for event in events}
    decisions = [event for event in events if event.get("event") == "router.decision"]
    label = decisions[-1].get("label") if decisions else None

    if label == "CHAT":
        if "fast.reply.end" in names:
            return "QWEN_FAST_CHAT"
        if "fast.reply.start" in names:
            return "QWEN_FAST_CHAT_STARTED_NO_END"
        return "MOSHI_ONLY"
    if label == "ACT":
        return "ACT_STUB"
    if label == "ASK":
        if "backend.inject.end" in names:
            return "QWEN_BACKEND_SPOKEN"
        if "backend.inject.start" in names:
            return "QWEN_BACKEND_INJECTION_STARTED_NO_END"
        if "backend.answer.ready" in names:
            return "QWEN_BACKEND_READY_QUEUED"
        if "ask.pending.start" in names and "fast.reply.end" in names:
            return "QWEN_ASK_PENDING_FAST_ONLY"
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
        parts.append(f"confidence={event.get('confidence', '-')}")
        parts.append(f"reason={short(event.get('reason', '-'), 80)}")
        if event.get("correlationId"):
            parts.append(f"correlationId={event.get('correlationId')}")
    elif name == "transcript.user":
        parts.append(f"text={short(event.get('text'))}")
    elif name == "transcript.moshi":
        parts.append(f"state={event.get('state', '-')}")
        parts.append(f"text={short(event.get('text'))}")
    elif name in {"transcript.fast", "transcript.backend"}:
        parts.append(f"text={short(event.get('text'))}")
    elif name == "router.input":
        parts.append(f"utterance={short(event.get('utteranceText'))}")
    elif name in {"fast.reply.request", "fast.reply.response", "fast.reply.start", "fast.reply.end", "fast.reply.canceled"}:
        parts.append(f"mode={event.get('promptMode', '-')}")
        parts.append(f"correlationId={event.get('correlationId', '-')}")
        if event.get("latencyMs") is not None:
            parts.append(f"latencyMs={event.get('latencyMs')}")
        if event.get("text"):
            parts.append(f"text={short(event.get('text'))}")
        if event.get("reason"):
            parts.append(f"reason={event.get('reason')}")
    elif name in {"ask.pending.start", "ask.pending.superseded"}:
        parts.append(f"correlationId={event.get('correlationId', '-')}")
        if event.get("question"):
            parts.append(f"question={short(event.get('question'))}")
    elif name in {"backend.answer.ready", "backend.answer.queued", "backend.inject.start", "backend.inject.end", "backend.inject.canceled"}:
        parts.append(f"correlationId={event.get('correlationId', '-')}")
        if event.get("reason"):
            parts.append(f"reason={event.get('reason')}")
    elif name == "llm.answer.request":
        parts.append(f"model={event.get('model', '-')}")
    elif name == "llm.answer.response":
        parts.append(f"latencyMs={event.get('latencyMs', '-')}")
        parts.append(f"text={short(event.get('text'))}")
    elif name == "llm.answer.error":
        parts.append(f"latencyMs={event.get('latencyMs', '-')}")
        parts.append(f"error={event.get('error', '-')}")
    elif name == "llm.harmonizer.request":
        parts.append(f"reintroduce={event.get('reintroduce', '-')}")
        parts.append(f"rawAnswer={short(event.get('rawAnswer'))}")
    elif name == "llm.harmonizer.response":
        parts.append(f"latencyMs={event.get('latencyMs', '-')}")
        parts.append(f"text={short(event.get('text'))}")
    elif name == "llm.harmonizer.error":
        parts.append(f"latencyMs={event.get('latencyMs', '-')}")
        parts.append(f"error={event.get('error', '-')}")
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
    elif name == "handoff.inject.text":
        parts.append(f"text={short(event.get('text'))}")
    elif name in {"inject.start", "inject.end", "barge_in"}:
        parts.append(f"correlationId={event.get('correlationId', '-')}")
    return " | ".join(parts)


def message_block(messages: Any) -> str:
    if not isinstance(messages, list):
        return full_text(messages)
    blocks = []
    for message in messages:
        if isinstance(message, dict):
            role = message.get("role", "unknown")
            content = full_text(message.get("content"))
        else:
            role = "message"
            content = full_text(message)
        blocks.append(f"{role}:\n{content}")
    return "\n\n---\n\n".join(blocks)


def moshi_events_for_turn(
        all_events: list[dict[str, Any]],
        session_id: str,
        start_ts: int,
        end_ts: int) -> list[dict[str, Any]]:
    return [
        event for event in all_events
        if event.get("event") == "transcript.moshi"
        and event.get("sessionId") == session_id
        and start_ts <= int(event.get("ts", 0)) <= end_ts
    ]


def next_turn_start(
        ordered_turns: list[tuple[tuple[str, str], list[dict[str, Any]]]],
        index: int,
        session_id: str) -> int | None:
    for (candidate_session, _), events in ordered_turns[index + 1:]:
        if candidate_session == session_id:
            return min(int(event.get("ts", 0)) for event in events)
    return None


def write_flow_log(
        events: list[dict[str, Any]],
        input_path: Path,
        output: Path,
        tail: int,
        session_id: str | None) -> None:
    if session_id:
        events = [event for event in events if event.get("sessionId") == session_id]

    correlation_to_key: dict[str, tuple[str, str]] = {}
    for event in events:
        correlation_id = event.get("correlationId")
        utterance_id = event.get("utteranceId")
        if correlation_id and utterance_id:
            correlation_to_key[str(correlation_id)] = event_key(event)

    turns: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    loose_events: list[dict[str, Any]] = []
    for event in events:
        name = event.get("event")
        if name not in KEY_EVENTS:
            continue
        key = event_key(event)
        if key[1]:
            turns[key].append(event)
        elif event.get("correlationId") and str(event.get("correlationId")) in correlation_to_key:
            turns[correlation_to_key[str(event.get("correlationId"))]].append(event)
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
        "This report groups user STT text, Moshi text, router decisions, backend LLM prompts/responses, harmonizer output, and injection timing by turn. It intentionally logs conversation text for debugging; do not share it if the conversation contains private content.",
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
    for index, ((turn_session, utterance_id), turn_events) in enumerate(recent_turns):
        sorted_events = sorted(turn_events, key=lambda event: int(event.get("ts", 0)))
        decision = next((event for event in sorted_events if event.get("event") == "router.decision"), {})
        user_event = first_event(sorted_events, "transcript.user") or first_event(sorted_events, "utterance.end")
        router_input = first_event(sorted_events, "router.input")
        fast_request = first_event(sorted_events, "fast.reply.request")
        fast_response = first_event(sorted_events, "fast.reply.response")
        fast_start = first_event(sorted_events, "fast.reply.start")
        fast_end = first_event(sorted_events, "fast.reply.end")
        answer_request = first_event(sorted_events, "llm.answer.request")
        answer_response = first_event(sorted_events, "llm.answer.response")
        answer_error = first_event(sorted_events, "llm.answer.error")
        harmonizer_request = first_event(sorted_events, "llm.harmonizer.request")
        harmonizer_response = first_event(sorted_events, "llm.harmonizer.response")
        harmonizer_error = first_event(sorted_events, "llm.harmonizer.error")
        inject_text = first_event(sorted_events, "handoff.inject.text")
        backend_ready = first_event(sorted_events, "backend.answer.ready") or first_event(sorted_events, "job.completed")
        backend_inject_start = first_event(sorted_events, "backend.inject.start") or first_event(sorted_events, "inject.start")
        backend_inject_end = first_event(sorted_events, "backend.inject.end") or first_event(sorted_events, "inject.end")
        inject_start = first_event(sorted_events, "inject.start")
        inject_end = first_event(sorted_events, "inject.end")
        first_audio = first_event(sorted_events, "moshi.first_audio")
        base_ts = int(user_event.get("ts", 0)) if user_event and user_event.get("ts") else None
        next_start = next_turn_start(recent_turns, index, turn_session)
        end_bound = next_start or (
            int(inject_end.get("ts", 0)) + 2000 if inject_end else int(sorted_events[-1].get("ts", 0)) + 15_000
        )
        moshi_around = moshi_events_for_turn(events, turn_session, int(sorted_events[0].get("ts", 0)), end_bound)
        label = decision.get("label", "-")
        result = verdict(sorted_events)
        lines.append(f"### {fmt_ts(sorted_events[0].get('ts'))} `{result}`")
        lines.append("")
        lines.append(f"- session: `{turn_session}`")
        lines.append(f"- utterance: `{utterance_id}`")
        if user_event and user_event.get("text"):
            lines.append(f"- user query: `{short(user_event.get('text'), 500)}`")
        lines.append(f"- router label: `{label}`")
        if decision:
            lines.append(f"- router confidence: `{decision.get('confidence', '-')}`")
            lines.append(f"- router reason: `{short(decision.get('reason', '-'), 200)}`")
        if decision.get("correlationId"):
            lines.append(f"- correlation: `{decision.get('correlationId')}`")
        timing_lines = [
            delta_line("router decision after user", decision, base_ts),
            delta_line("fast reply start after user", fast_start, base_ts),
            delta_line("fast reply end after user", fast_end, base_ts),
            delta_line("first Moshi audio after user", first_audio, base_ts),
            delta_line("backend raw answer after user", answer_response, base_ts),
            delta_line("backend answer ready after user", backend_ready, base_ts),
            delta_line("backend answer error after user", answer_error, base_ts),
            delta_line("harmonized response after user", harmonizer_response, base_ts),
            delta_line("harmonizer error after user", harmonizer_error, base_ts),
            delta_line("backend injection start after user", backend_inject_start, base_ts),
            delta_line("backend injection end after user", backend_inject_end, base_ts),
        ]
        timing_lines = [line for line in timing_lines if line]
        if timing_lines:
            lines.append("- timing:")
            lines.extend(f"  {line}" for line in timing_lines)

        if moshi_around:
            lines.append("- Moshi text around this turn:")
            for event in moshi_around:
                lines.append(f"  - {fmt_ts(event.get('ts'))} `{event.get('state', '-')}`: {short(event.get('text'), 500)}")

        if router_input and router_input.get("transcriptWindow"):
            lines.append("- router transcript window:")
            lines.append("```text")
            lines.append(full_text(router_input.get("transcriptWindow")))
            lines.append("```")

        if fast_request:
            lines.append("- fast Qwen request:")
            lines.append(f"  - mode: `{fast_request.get('promptMode', '-')}`")
            lines.append(f"  - model: `{fast_request.get('model', '-')}`")
            lines.append("```text")
            lines.append(message_block(fast_request.get("messages")))
            lines.append("```")

        if fast_response:
            lines.append("- fast Qwen spoken text:")
            lines.append("```text")
            lines.append(full_text(fast_response.get("text")))
            lines.append("```")

        if answer_request:
            lines.append("- backend answer LLM request:")
            lines.append(f"  - model: `{answer_request.get('model', '-')}`")
            lines.append("```text")
            lines.append(message_block(answer_request.get("messages")))
            lines.append("```")

        if answer_response:
            lines.append("- backend raw answer:")
            lines.append("```text")
            lines.append(full_text(answer_response.get("text")))
            lines.append("```")
        if answer_error:
            lines.append(f"- backend answer error: `{answer_error.get('error', '-')}`")

        if harmonizer_request:
            lines.append("- harmonizer input:")
            lines.append("```text")
            lines.append("REINTRODUCE=%s\n\nRecent transcript:\n%s\n\nRaw answer:\n%s" % (
                harmonizer_request.get("reintroduce", "-"),
                full_text(harmonizer_request.get("transcriptWindow")),
                full_text(harmonizer_request.get("rawAnswer")),
            ))
            lines.append("```")

        if harmonizer_response:
            lines.append("- harmonized spoken response:")
            lines.append("```text")
            lines.append(full_text(harmonizer_response.get("text")))
            lines.append("```")
        if harmonizer_error:
            lines.append(f"- harmonizer error: `{harmonizer_error.get('error', '-')}`")
        if inject_text:
            lines.append("- text sent to TTS/injection:")
            lines.append("```text")
            lines.append(full_text(inject_text.get("text")))
            lines.append("```")

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
