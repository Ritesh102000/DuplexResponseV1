# Model Decisions

Date: 2026-06-12

## Summary

- Hardware: Apple Mac M4, 16 GB unified memory.
- Moshi: local Apple Silicon MLX q4, `kyutai/moshiko-mlx-q4`.
- Voice: `moshiko` male.
- Backend LLM: hosted OpenAI-compatible API.
- Models: `gpt-5.4-mini` for answer, router, harmonizer, and judge.
- STT: faster-whisper small CPU with Silero VAD.
- TTS: Piper CPU, natural male English voice.
- API budget: 20 USD/month.
- Demo topology: Mac + API.

## Interview Answers

1. What GPU/hardware will run Moshi?
   Answer: Apple Mac M4 with 16 GB unified memory.
   Consequence: Use local Apple Silicon MLX q4 for real Moshi; keep stub mode default for CI.

2. Moshi voice variant?
   Answer: `moshiko` male.
   Consequence: Pick a male Piper voice for injection.

3. Backend answer model?
   Answer: Hosted OpenAI-compatible API, `gpt-5.4-mini`.
   Consequence: Good quality/cost tradeoff with no local LLM memory pressure.

4. Router model?
   Answer: `gpt-5.4-mini` in real mode with heuristic fallback.
   Consequence: Simple config and low operational complexity.

5. Harmonizer and judge?
   Answer: Reuse `gpt-5.4-mini`.
   Consequence: One model family for all LLM tasks.

6. STT?
   Answer: faster-whisper small CPU, English with Indian accent.
   Consequence: Avoids GPU contention with Moshi.

7. TTS?
   Answer: Piper CPU, natural male English voice.
   Consequence: Local CPU TTS remains feasible on Mac.

8. Monthly API budget cap?
   Answer: 20 USD/month.
   Consequence: Add call caps and keep evals modest.

9. Final demo topology?
   Answer: Mac + API.
   Consequence: Moshi/STT/TTS local; backend LLM hosted.

