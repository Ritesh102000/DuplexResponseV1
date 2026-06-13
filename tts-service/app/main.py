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
    if can_use_macos_say():
        engine = "macos_say"
    elif can_use_espeak_ng():
        engine = "espeak_ng"
    else:
        engine = "stub_sine"
    return {"status": "UP", "engine": engine}


@app.post("/speak")
def speak(request: SpeakRequest) -> Response:
    text = request.text.strip()
    wav_bytes = (
        synthesize_macos_say(text)
        or synthesize_espeak_ng(text)
        or wav_from_pcm(synthesize_stub_pcm(text))
    )
    return Response(content=wav_bytes, media_type="audio/wav")


def can_use_macos_say() -> bool:
    return shutil.which("say") is not None and shutil.which("afconvert") is not None


def can_use_espeak_ng() -> bool:
    return shutil.which("espeak-ng") is not None


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


def synthesize_espeak_ng(text: str) -> bytes | None:
    if not can_use_espeak_ng():
        return None

    voice = os.getenv("TTS_ESPEAK_VOICE", os.getenv("TTS_VOICE", "en-us")).strip() or "en-us"
    speed = os.getenv("TTS_ESPEAK_SPEED", "165").strip() or "165"
    try:
        result = subprocess.run(
            ["espeak-ng", "--stdout", "-v", voice, "-s", speed, text],
            check=True,
            timeout=12,
            capture_output=True,
        )
        return normalize_wav_to_contract(result.stdout)
    except (OSError, subprocess.SubprocessError, wave.Error):
        return None


def normalize_wav_to_contract(wav_bytes: bytes) -> bytes:
    with wave.open(io.BytesIO(wav_bytes), "rb") as wav_file:
        channels = wav_file.getnchannels()
        sample_width = wav_file.getsampwidth()
        sample_rate = wav_file.getframerate()
        frames = wav_file.readframes(wav_file.getnframes())

    pcm = to_mono_pcm16(frames, channels, sample_width)
    if sample_rate != SAMPLE_RATE:
        pcm = resample_pcm16(pcm, sample_rate, SAMPLE_RATE)
    return wav_from_pcm(pcm)


def to_mono_pcm16(frames: bytes, channels: int, sample_width: int) -> bytes:
    if channels < 1:
        return b""

    output = bytearray()
    frame_width = channels * sample_width
    for offset in range(0, len(frames) - frame_width + 1, frame_width):
        total = 0
        for channel in range(channels):
            start = offset + channel * sample_width
            total += sample_to_int16(frames[start:start + sample_width], sample_width)
        output.extend(struct.pack("<h", int(total / channels)))
    return bytes(output)


def sample_to_int16(sample: bytes, sample_width: int) -> int:
    if sample_width == 2:
        return struct.unpack("<h", sample)[0]
    if sample_width == 1:
        return (sample[0] - 128) << 8
    if sample_width == 4:
        return max(-32768, min(32767, struct.unpack("<i", sample)[0] >> 16))
    return 0


def resample_pcm16(pcm: bytes, source_rate: int, target_rate: int) -> bytes:
    if not pcm or source_rate <= 0:
        return pcm

    samples = [struct.unpack("<h", pcm[i:i + 2])[0] for i in range(0, len(pcm) - 1, 2)]
    if not samples:
        return b""

    target_count = max(1, int(round(len(samples) * target_rate / source_rate)))
    output = bytearray()
    for index in range(target_count):
        source_pos = index * source_rate / target_rate
        left = int(source_pos)
        right = min(left + 1, len(samples) - 1)
        fraction = source_pos - left
        value = int(samples[left] * (1.0 - fraction) + samples[right] * fraction)
        output.extend(struct.pack("<h", max(-32768, min(32767, value))))
    return bytes(output)


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
