from __future__ import annotations

import io
import math
import os
import shutil
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

from fastapi import FastAPI, Response
from pydantic import BaseModel, Field


SAMPLE_RATE = 24_000
SAMPLE_WIDTH_BYTES = 2
CHANNELS = 1

app = FastAPI(title="voice-two-brains-tts")


class SpeakRequest(BaseModel):
    text: str = Field(min_length=1, max_length=800)


@app.get("/health")
def health() -> dict[str, str]:
    engine = "macos_say" if can_use_macos_say() else "stub_sine"
    return {"status": "UP", "engine": engine}


@app.post("/speak")
def speak(request: SpeakRequest) -> Response:
    text = request.text.strip()
    wav_bytes = synthesize_macos_say(text) or wav_from_pcm(synthesize_stub_pcm(text))
    return Response(content=wav_bytes, media_type="audio/wav")


def can_use_macos_say() -> bool:
    return shutil.which("say") is not None and shutil.which("afconvert") is not None


def synthesize_macos_say(text: str) -> bytes | None:
    if not can_use_macos_say():
        return None

    voice = os.getenv("TTS_VOICE", "").strip()
    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = Path(tmp)
        aiff_path = tmpdir / "speech.aiff"
        wav_path = tmpdir / "speech.wav"

        say_command = ["say", "-o", str(aiff_path)]
        if voice:
            say_command.extend(["-v", voice])
        say_command.append(text)

        try:
            subprocess.run(say_command, check=True, timeout=12, capture_output=True)
            subprocess.run(
                [
                    "afconvert",
                    "-f",
                    "WAVE",
                    "-d",
                    f"LEI16@{SAMPLE_RATE}",
                    "-c",
                    str(CHANNELS),
                    str(aiff_path),
                    str(wav_path),
                ],
                check=True,
                timeout=8,
                capture_output=True,
            )
            return wav_path.read_bytes()
        except (OSError, subprocess.SubprocessError):
            return None


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
