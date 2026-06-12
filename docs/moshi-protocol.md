# Moshi WebSocket Protocol

Phase 1 source inspection date: 2026-06-12.

Sources inspected from `kyutai-labs/moshi` shallow clone:
- `rust/protocol.md`
- `rust/moshi-server/src/protocol.rs`
- `rust/moshi-server/src/lm.rs`
- `rust/moshi-server/src/mimi.rs`
- `moshi/moshi/client.py`
- `client/src/protocol/types.ts`
- `client/src/protocol/encoder.ts`
- `client/src/pages/Conversation/hooks/useSocket.ts`

## Endpoint

The normal streaming conversation endpoint is:

```text
/api/chat
```

The Python client builds `ws://<host>:8998/api/chat` by default. The Rust standalone server also routes `/api/chat` to the streaming handler.

## WebSocket Message Shape

All application messages are binary WebSocket frames. The first byte is the message type. The remaining bytes are the payload.

Current source message types:

| Type byte | Name | Payload |
|---:|---|---|
| `0` | `Handshake` | Protocol/model version bytes. Server sends 9 total bytes: type byte plus eight zero bytes. Web client encoder sends type byte plus one version byte plus one model byte. Treat missing high bytes as zero. |
| `1` | `Audio` | Ogg pages containing Opus-encoded 24 kHz mono audio. |
| `2` | `Text` | UTF-8 text token/string from Moshi's inner monologue. |
| `3` | `Control` | One byte: `0=start`, `1=endTurn`, `2=pause`, `3=restart`. Not used by full streaming mode in Phase 1. |
| `4` | `Metadata` | UTF-8 JSON string. |
| `5` | `Error` | UTF-8 error description. |
| `6` | `Ping` | No payload. Currently unused. |
| `7` | `ColoredText` | One color byte followed by UTF-8 text. Present in current client/server enum. |
| `8` | `Image` | Present in current server enum, not used for this project. |
| `9` | `Codes` | Current server enum supports code messages; not used by this project. |

Unknown message types must be ignored or logged and discarded.

## Handshake

Server-side streaming handlers send a binary handshake immediately after WebSocket connect:

```text
00 00 00 00 00 00 00 00 00
```

That is `MT=0`, protocol version `0`, model version `0`.

The web client marks the socket connected when it receives a decoded handshake.

## Audio

Moshi audio frames are `MT=1` followed by Ogg/Opus bytes:

```text
01 <ogg-opus-bytes>
```

The Python client wraps bytes emitted by `sphn.OpusStreamWriter(24000)` with `0x01` when sending audio to the server. On receive it strips `0x01` and feeds the payload into `sphn.OpusStreamReader(24000)`.

The Rust server uses an Ogg/Opus encoder/decoder at 24 kHz. It sends an `Audio` message containing the Opus header data before streaming encoded pages.

The gateway browser contract remains different: browser-to-gateway and gateway-to-browser use raw 24 kHz, signed 16-bit, mono PCM chunks. Therefore the real Moshi client is responsible for PCM <-> Ogg/Opus conversion when `MOSHI_MODE=real`.

## Text

Moshi text is `MT=2` followed by UTF-8 bytes:

```text
02 <utf8-token-or-string>
```

Text messages represent Moshi's streamed text/inner monologue and are forwarded by the gateway to the browser as `transcript.moshi` JSON control messages.

## Phase 1 Gateway Stub Protocol

CI and local tests use `MOSHI_MODE=stub`. The Java `StubMoshiClient` does not pretend to be real Moshi; it is a gateway test double that echoes raw browser PCM back to the browser so the browser-gateway-Moshi path can be tested without Opus, GPU, or network model access.

The separate `stubs/fake-moshi/` Python server mirrors the documented Moshi message envelope for later real-client tests: it sends the 9-byte handshake, echoes `Audio` payloads as `Audio`, and can emit `Text`.

