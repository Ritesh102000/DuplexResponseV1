from __future__ import annotations

import io
import math
import struct
import wave

from fastapi import FastAPI, Response
from pydantic import BaseModel, Field


SAMPLE_RATE = 24_000
SAMPLE_WIDTH_BYTES = 2
CHANNELS = 1

app = FastAPI(title="voice-two-brains-tts")


class SpeakRequest(BaseModel):
    text: str = Field(min_length=1)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/speak")
def speak(request: SpeakRequest) -> Response:
    text = request.text.strip()
    pcm = synthesize_stub_pcm(text)
    wav_bytes = wav_from_pcm(pcm)
    return Response(content=wav_bytes, media_type="audio/wav")


def synthesize_stub_pcm(text: str) -> bytes:
    # CI-safe placeholder behind the TTS sidecar contract. Replace this function
    # with Piper invocation when a concrete voice/model path is available.
    duration_seconds = max(0.45, min(2.8, 0.045 * len(text)))
    sample_count = int(SAMPLE_RATE * duration_seconds)
    frequency = 560.0
    amplitude = 0.18
    frames = bytearray()
    for i in range(sample_count):
        envelope = min(1.0, i / 1200, (sample_count - i) / 1200)
        value = math.sin(2 * math.pi * frequency * (i / SAMPLE_RATE))
        sample = int(32767 * amplitude * envelope * value)
        frames.extend(struct.pack("<h", sample))
    return bytes(frames)


def wav_from_pcm(pcm: bytes) -> bytes:
    output = io.BytesIO()
    with wave.open(output, "wb") as wav_file:
        wav_file.setnchannels(CHANNELS)
        wav_file.setsampwidth(SAMPLE_WIDTH_BYTES)
        wav_file.setframerate(SAMPLE_RATE)
        wav_file.writeframes(pcm)
    return output.getvalue()
