# Fake Moshi Stub

Protocol-compatible fake Moshi WebSocket server for local gateway testing.

It sends the Moshi 9-byte handshake, emits a text token, and echoes `MT=1`
audio payloads back as `MT=1` audio.

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
python fake_moshi.py --port 8998
```

Phase 4 suppression fixtures:

```sh
python fake_moshi.py --port 8998 --fixture ack
python fake_moshi.py --port 8998 --fixture long-answer
```

`ack` emits a short Moshi acknowledgment before echoing audio. `long-answer`
emits a long Moshi text answer before echoing audio, which lets the gateway
exercise its ASK-in-flight suppression gate.
