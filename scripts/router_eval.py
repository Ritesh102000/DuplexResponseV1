#!/usr/bin/env python3
import json
import sys
from collections import Counter, defaultdict


ASK_PREFIXES = (
    "what", "who", "why", "when", "where", "which", "how",
    "is", "are", "does", "do", "did", "could you compare",
    "can you summarize", "can you explain", "can you give",
    "can you list", "can you make the answer", "explain",
)

ACT_PREFIXES = (
    "send", "book", "remind", "add", "schedule", "email", "turn",
    "set", "create", "order", "save", "open", "mute", "start",
    "share", "make a note", "call", "cancel", "move", "download",
    "text", "pause", "switch", "restart", "enable", "copy",
    "raise", "delete", "invite", "mark", "post",
)


def normalize(text: str) -> str:
    value = " ".join("".join(ch.lower() if ch.isalnum() else " " for ch in text).split())
    if value.startswith("please "):
        return value[len("please "):]
    return value


def classify(text: str) -> str:
    value = normalize(text)
    if any(value == p or value.startswith(p + " ") for p in ACT_PREFIXES):
        return "ACT"
    if any(value == p or value.startswith(p + " ") for p in ASK_PREFIXES):
        return "ASK"
    if any(cue in value for cue in (
        " explain ", " summarize ", " definition ", " difference between ",
        " compare ", " formula ", " calculate ", " command to ",
        " how do i ", " how can i ",
    )):
        return "ASK"
    return "CHAT"


def main(path: str) -> int:
    matrix: dict[str, Counter[str]] = defaultdict(Counter)
    total = 0
    correct = 0
    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            item = json.loads(line)
            expected = item["label"]
            predicted = classify(item["text"])
            matrix[expected][predicted] += 1
            total += 1
            correct += int(expected == predicted)

    print(f"accuracy={correct / total:.3f} ({correct}/{total})")
    labels = ("CHAT", "ASK", "ACT")
    print("confusion_matrix rows=expected cols=predicted")
    print("," + ",".join(labels))
    for expected in labels:
        print(expected + "," + ",".join(str(matrix[expected][predicted]) for predicted in labels))
    return 0 if correct == total else 1


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: router_eval.py <router-labels.jsonl>", file=sys.stderr)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
