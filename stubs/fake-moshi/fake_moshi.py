#!/usr/bin/env python3
"""Protocol-compatible fake Moshi WebSocket server for Phase 1 tests."""

import argparse
import asyncio
import json
import signal

import websockets


HANDSHAKE = bytes([0, 0, 0, 0, 0, 0, 0, 0, 0])
TEXT_PREFIX = bytes([2])
AUDIO_PREFIX = bytes([1])


async def handler(websocket):
    await websocket.send(HANDSHAKE)
    await websocket.send(TEXT_PREFIX + b"fake moshi ready")
    async for message in websocket:
        if not isinstance(message, bytes) or not message:
            continue
        kind = message[0]
        payload = message[1:]
        if kind == 1:
            await websocket.send(AUDIO_PREFIX + payload)
        elif kind == 3 and payload[:1] == b"\x03":
            await websocket.close()
        elif kind == 4:
            metadata = json.loads(payload.decode("utf-8"))
            await websocket.send(TEXT_PREFIX + f"metadata:{metadata}".encode("utf-8"))


async def main(port: int):
    stop = asyncio.Future()
    loop = asyncio.get_running_loop()
    loop.add_signal_handler(signal.SIGTERM, stop.set_result, None)
    async with websockets.serve(handler, "0.0.0.0", port):
        await stop


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8998)
    args = parser.parse_args()
    asyncio.run(main(args.port))

