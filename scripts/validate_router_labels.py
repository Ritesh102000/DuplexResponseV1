#!/usr/bin/env python3
import json
import sys
from collections import Counter


EXPECTED = {"CHAT": 50, "ASK": 50, "ACT": 40}
REQUIRED_FIELDS = {"id", "text", "label", "ambiguous"}


def main(path: str) -> int:
    counts = Counter()
    ambiguous = 0
    seen_ids = set()

    with open(path, "r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                raise ValueError(f"line {line_number}: blank lines are not allowed")
            item = json.loads(line)
            missing = REQUIRED_FIELDS - item.keys()
            if missing:
                raise ValueError(f"line {line_number}: missing fields {sorted(missing)}")
            if item["id"] in seen_ids:
                raise ValueError(f"line {line_number}: duplicate id {item['id']}")
            if item["label"] not in EXPECTED:
                raise ValueError(f"line {line_number}: invalid label {item['label']}")
            if not isinstance(item["text"], str) or not item["text"].strip():
                raise ValueError(f"line {line_number}: text must be non-empty")
            if not isinstance(item["ambiguous"], bool):
                raise ValueError(f"line {line_number}: ambiguous must be boolean")

            seen_ids.add(item["id"])
            counts[item["label"]] += 1
            ambiguous += int(item["ambiguous"])

    if counts != EXPECTED:
        raise ValueError(f"label counts {dict(counts)} do not match {EXPECTED}")
    if ambiguous < 20:
        raise ValueError(f"expected at least 20 ambiguous examples, found {ambiguous}")

    print(f"validated {sum(counts.values())} labels: {dict(counts)}, ambiguous={ambiguous}")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: validate_router_labels.py <router-labels.jsonl>", file=sys.stderr)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))

