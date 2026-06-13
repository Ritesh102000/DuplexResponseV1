from __future__ import annotations

import asyncio
import importlib.util
import os
import tempfile
import threading
import time
from typing import Any

from fastapi import FastAPI, HTTPException, Query, Request
from pydantic import BaseModel


app = FastAPI(title="voice-two-brains-stt")

MODEL_NAME = os.getenv("STT_WHISPER_MODEL", "small")
DEVICE = os.getenv("STT_DEVICE", "cpu")
COMPUTE_TYPE = os.getenv("STT_COMPUTE_TYPE", "int8")
LANGUAGE = os.getenv("STT_LANGUAGE", "en").strip() or None
BEAM_SIZE = int(os.getenv("STT_BEAM_SIZE", "1"))

_model_lock = threading.Lock()
_model: Any | None = None


class StubUtteranceRequest(BaseModel):
    text: str
    endTs: int | None = None


class UtteranceResponse(BaseModel):
    text: str
    endTs: int


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "UP",
        "engine": "faster_whisper" if _faster_whisper_available() else "missing_faster_whisper",
        "model": MODEL_NAME,
        "device": DEVICE,
        "computeType": COMPUTE_TYPE,
        "language": LANGUAGE or "auto",
        "modelLoaded": _model is not None,
    }


@app.post("/stub/utterance", response_model=UtteranceResponse)
def stub_utterance(request: StubUtteranceRequest) -> UtteranceResponse:
    return UtteranceResponse(
        text=request.text.strip(),
        endTs=request.endTs or int(time.time() * 1000),
    )


@app.post("/transcribe", response_model=list[UtteranceResponse])
async def transcribe(
        request: Request,
        endTs: int | None = Query(default=None)) -> list[UtteranceResponse]:
    audio = await request.body()
    if not audio:
        return []
    text = await asyncio.to_thread(_transcribe_wav, audio)
    text = text.strip()
    if not text:
        return []
    return [UtteranceResponse(text=text, endTs=endTs or int(time.time() * 1000))]


def _faster_whisper_available() -> bool:
    return importlib.util.find_spec("faster_whisper") is not None


def _load_model() -> Any:
    global _model
    if _model is not None:
        return _model
    with _model_lock:
        if _model is not None:
            return _model
        try:
            from faster_whisper import WhisperModel
        except ImportError as exc:
            raise HTTPException(
                status_code=503,
                detail="faster-whisper is not installed; install stt-service/requirements-real.txt",
            ) from exc
        _model = WhisperModel(MODEL_NAME, device=DEVICE, compute_type=COMPUTE_TYPE)
        return _model


def _transcribe_wav(audio: bytes) -> str:
    model = _load_model()
    with tempfile.NamedTemporaryFile(suffix=".wav") as handle:
        handle.write(audio)
        handle.flush()
        segments, _info = model.transcribe(
            handle.name,
            language=LANGUAGE,
            vad_filter=True,
            beam_size=BEAM_SIZE,
        )
        return " ".join(segment.text.strip() for segment in segments).strip()
