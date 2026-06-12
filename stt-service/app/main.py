from __future__ import annotations

import time
from pydantic import BaseModel
from fastapi import FastAPI


app = FastAPI(title="voice-two-brains-stt")


class StubUtteranceRequest(BaseModel):
    text: str
    endTs: int | None = None


class UtteranceResponse(BaseModel):
    text: str
    endTs: int


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/stub/utterance", response_model=UtteranceResponse)
def stub_utterance(request: StubUtteranceRequest) -> UtteranceResponse:
    return UtteranceResponse(
        text=request.text.strip(),
        endTs=request.endTs or int(time.time() * 1000),
    )


@app.post("/transcribe", response_model=list[UtteranceResponse])
async def transcribe() -> list[UtteranceResponse]:
    # Phase 2 scaffold: real Silero VAD + faster-whisper streaming is configured
    # behind this sidecar in a later real-runtime pass. CI uses gateway stub mode.
    return []

