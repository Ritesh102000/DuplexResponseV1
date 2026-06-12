# Fake Moshi Stub

Protocol-compatible fake Moshi WebSocket server for local Phase 1 testing.

It sends the Moshi 9-byte handshake, emits a text token, and echoes `MT=1`
audio payloads back as `MT=1` audio.

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
python fake_moshi.py --port 8998
```
