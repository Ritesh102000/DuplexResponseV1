# Architecture

This document records the Phase 0 architecture artifacts. Later phases must keep it current as behavior is implemented.

## Gateway Components

```mermaid
flowchart LR
    Browser[Browser client] -->|PCM WS| BrowserSocketHandler
    BrowserSocketHandler --> AudioInboundPipeline
    AudioInboundPipeline --> MoshiClient
    AudioInboundPipeline --> SttClient
    SttClient --> SessionStateMachine
    MoshiClient --> SuppressionGate
    SuppressionGate --> OutboundMixer
    SessionStateMachine --> RouterService
    RouterService --> AskJobService
    AskJobService --> LlmClient
    AskJobService --> Harmonizer
    Harmonizer --> TtsClient
    TtsClient --> OutboundMixer
    OutboundMixer -->|PCM WS| Browser
    SessionStateMachine --> EventLogger
```

## Happy ASK Sequence

```mermaid
sequenceDiagram
    participant B as Browser
    participant G as Gateway
    participant M as Moshi
    participant S as STT
    participant L as LLM
    participant T as TTS

    B->>G: PCM frames
    G->>M: audio stream
    G->>S: audio stream
    S-->>G: utterance.end
    G->>L: router classify
    L-->>G: ASK
    G-->>B: router.decision
    G->>L: answer job
    M-->>G: acknowledgment audio
    G-->>B: acknowledgment audio
    L-->>G: raw answer
    G->>L: harmonize
    L-->>G: spoken answer
    G->>T: speak
    G-->>B: inject.start + TTS audio
    G-->>B: inject.end
```

## Stale Answer Sequence

```mermaid
sequenceDiagram
    participant G as Gateway
    participant L as LLM
    participant B as Browser

    G->>L: answer job with turn index
    G->>G: user turn advances
    L-->>G: job result
    alt stale but close
        G-->>B: inject.start with reintroduced answer
    else too stale
        G-->>B: job.dropped_stale
    end
```

## Barge-In Sequence

```mermaid
sequenceDiagram
    participant B as Browser
    participant G as Gateway
    participant T as TTS

    G->>T: stream answer audio
    G-->>B: inject.start
    B->>G: user speech during injection
    G->>G: cancel TTS stream
    G-->>B: inject.end
    G->>G: return to LISTENING
```

