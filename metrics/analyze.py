#!/usr/bin/env python3
"""Analyze gateway JSONL events and emit Phase 5 demo metrics."""

from __future__ import annotations

import argparse
import csv
import json
import math
import statistics
import struct
import zlib
from collections import Counter, defaultdict
from pathlib import Path


LABELS = ("CHAT", "ASK", "ACT")


def read_events(path: Path) -> list[dict]:
    events: list[dict] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, 1):
            if not line.strip():
                continue
            try:
                events.append(json.loads(line))
            except json.JSONDecodeError as exc:
                raise SystemExit(f"{path}:{line_number}: invalid JSON: {exc}") from exc
    return events


def key(event: dict) -> tuple[str, str | None]:
    return event.get("sessionId", ""), event.get("utteranceId")


def ask_latency_rows(events: list[dict]) -> list[dict]:
    utterance_end = {}
    asks = {}
    first_audio = {}
    inject_start = {}
    job_completed = {}

    for event in events:
        name = event.get("event")
        if name == "utterance.end":
            utterance_end[key(event)] = event
        elif name == "router.decision" and event.get("label") == "ASK":
            correlation_id = event.get("correlationId")
            if correlation_id:
                asks[correlation_id] = event
        elif name == "moshi.first_audio":
            first_audio[event.get("correlationId")] = event
        elif name == "inject.start":
            inject_start[event.get("correlationId")] = event
        elif name == "job.completed":
            job_completed[event.get("correlationId")] = event

    rows = []
    for correlation_id, decision in asks.items():
        end = utterance_end.get(key(decision))
        if not end:
            continue
        end_ts = int(end["ts"])
        perceived = delta_ms(first_audio.get(correlation_id), end_ts)
        true_latency = delta_ms(inject_start.get(correlation_id), end_ts)
        rows.append(
            {
                "sessionId": decision.get("sessionId", ""),
                "utteranceId": decision.get("utteranceId", ""),
                "correlationId": correlation_id,
                "perceivedMs": perceived,
                "trueMs": true_latency,
                "jobLatencyMs": int(job_completed[correlation_id]["latencyMs"])
                if correlation_id in job_completed and "latencyMs" in job_completed[correlation_id]
                else None,
            }
        )
    return rows


def delta_ms(event: dict | None, base_ts: int) -> int | None:
    if not event or "ts" not in event:
        return None
    return max(0, int(event["ts"]) - base_ts)


def suppression_rate(events: list[dict]) -> dict:
    ask_count = sum(1 for event in events if event.get("event") == "router.decision" and event.get("label") == "ASK")
    faded = sum(1 for event in events if event.get("event") == "suppression.faded")
    return {
        "askCount": ask_count,
        "suppressionFadedCount": faded,
        "suppressionRate": faded / ask_count if ask_count else 0.0,
    }


def router_confusion(events: list[dict]) -> tuple[list[list[int]], Counter]:
    matrix = [[0 for _ in LABELS] for _ in LABELS]
    predictions = Counter()
    label_index = {label: i for i, label in enumerate(LABELS)}
    for event in events:
        if event.get("event") != "router.decision":
            continue
        predicted = event.get("label")
        if predicted in LABELS:
            predictions[predicted] += 1
        expected = event.get("expectedLabel")
        if expected in label_index and predicted in label_index:
            matrix[label_index[expected]][label_index[predicted]] += 1
    return matrix, predictions


def percentile(values: list[int], pct: float) -> float | None:
    if not values:
        return None
    sorted_values = sorted(values)
    index = (len(sorted_values) - 1) * pct
    lower = math.floor(index)
    upper = math.ceil(index)
    if lower == upper:
        return float(sorted_values[int(index)])
    weight = index - lower
    return sorted_values[lower] * (1 - weight) + sorted_values[upper] * weight


def write_latency_csv(rows: list[dict], path: Path) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=["sessionId", "utteranceId", "correlationId", "perceivedMs", "trueMs", "jobLatencyMs"],
        )
        writer.writeheader()
        writer.writerows(rows)


def write_confusion_csv(matrix: list[list[int]], predictions: Counter, path: Path) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["expected\\predicted", *LABELS])
        for label, row in zip(LABELS, matrix):
            writer.writerow([label, *row])
        writer.writerow([])
        writer.writerow(["predicted_only", *LABELS])
        writer.writerow(["count", *(predictions[label] for label in LABELS)])


def write_latency_png(rows: list[dict], path: Path) -> None:
    width, height = 960, 540
    pixels = bytearray([255, 255, 255] * width * height)

    def set_px(x: int, y: int, color: tuple[int, int, int]) -> None:
        if 0 <= x < width and 0 <= y < height:
            offset = (y * width + x) * 3
            pixels[offset : offset + 3] = bytes(color)

    def line(x0: int, y0: int, x1: int, y1: int, color: tuple[int, int, int]) -> None:
        dx = abs(x1 - x0)
        sx = 1 if x0 < x1 else -1
        dy = -abs(y1 - y0)
        sy = 1 if y0 < y1 else -1
        err = dx + dy
        while True:
            set_px(x0, y0, color)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def rect(x0: int, y0: int, x1: int, y1: int, color: tuple[int, int, int]) -> None:
        for y in range(max(0, y0), min(height, y1 + 1)):
            for x in range(max(0, x0), min(width, x1 + 1)):
                set_px(x, y, color)

    chart_rows = [row for row in rows if row["perceivedMs"] is not None or row["trueMs"] is not None]
    values = [value for row in chart_rows for value in (row["perceivedMs"], row["trueMs"]) if value is not None]
    max_value = max(values, default=1000)
    max_value = max(1000, int(max_value * 1.15))
    left, right, top, bottom = 90, width - 60, 60, height - 80
    line(left, bottom, right, bottom, (30, 30, 30))
    line(left, top, left, bottom, (30, 30, 30))
    for tick in range(0, 6):
        y = bottom - int((bottom - top) * tick / 5)
        line(left - 6, y, right, y, (220, 220, 220))
    if not chart_rows:
        write_png(path, width, height, pixels)
        return
    group_width = max(18, (right - left) // max(1, len(chart_rows)))
    perceived_color = (44, 123, 182)
    true_color = (215, 48, 39)
    for index, row in enumerate(chart_rows):
        x = left + index * group_width + group_width // 2
        for offset, field, color in [(-7, "perceivedMs", perceived_color), (7, "trueMs", true_color)]:
            value = row[field]
            if value is None:
                continue
            bar_height = int((bottom - top) * value / max_value)
            rect(x + offset - 5, bottom - bar_height, x + offset + 5, bottom, color)
    write_png(path, width, height, pixels)


def write_png(path: Path, width: int, height: int, pixels: bytearray) -> None:
    raw = bytearray()
    row_bytes = width * 3
    for y in range(height):
        raw.append(0)
        raw.extend(pixels[y * row_bytes : (y + 1) * row_bytes])

    def chunk(kind: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + kind
            + data
            + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
        )

    png = b"".join(
        [
            b"\x89PNG\r\n\x1a\n",
            chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)),
            chunk(b"IDAT", zlib.compress(bytes(raw), 9)),
            chunk(b"IEND", b""),
        ]
    )
    path.write_bytes(png)


def write_summary(events: list[dict], rows: list[dict], output_dir: Path) -> dict:
    perceived = [row["perceivedMs"] for row in rows if row["perceivedMs"] is not None]
    true_latency = [row["trueMs"] for row in rows if row["trueMs"] is not None]
    suppression = suppression_rate(events)
    summary = {
        "eventCount": len(events),
        "askLatencyCount": len(rows),
        "perceivedMs": {
            "median": statistics.median(perceived) if perceived else None,
            "p95": percentile(perceived, 0.95),
        },
        "trueMs": {
            "median": statistics.median(true_latency) if true_latency else None,
            "p95": percentile(true_latency, 0.95),
        },
        "suppression": suppression,
        "bargeInCount": sum(1 for event in events if event.get("event") == "barge_in"),
        "staleDropCount": sum(1 for event in events if event.get("event") == "job.dropped_stale"),
    }
    (output_dir / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    return summary


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("events", nargs="?", default="metrics/fixtures/events.jsonl")
    parser.add_argument("--out", default="metrics/out")
    args = parser.parse_args()

    event_path = Path(args.events)
    output_dir = Path(args.out)
    output_dir.mkdir(parents=True, exist_ok=True)

    events = read_events(event_path)
    rows = ask_latency_rows(events)
    matrix, predictions = router_confusion(events)
    write_latency_csv(rows, output_dir / "ask_latencies.csv")
    write_confusion_csv(matrix, predictions, output_dir / "router_confusion_matrix.csv")
    write_latency_png(rows, output_dir / "latency_chart.png")
    summary = write_summary(events, rows, output_dir)
    print(json.dumps(summary, indent=2))
    print(f"wrote {output_dir / 'latency_chart.png'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
