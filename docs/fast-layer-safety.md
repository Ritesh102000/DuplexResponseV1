# Fast Layer Safety: Fine-Tune + Sanitize + Verifier

Goal: keep the fast local model interactive and natural without letting it answer factual `ASK_PENDING` questions before the backend answer model speaks.

## 1. Fine-Tune For Conversation, Not Truth

Fine-tune the fast model, likely `qwen2.5:1.5b` or `qwen2.5:3b`, to behave like a short spoken conversational layer.

Training examples should teach:
- short, warm, natural replies;
- no reasoning text;
- no direct factual answers in `ASK_PENDING`;
- smooth continuation while another model checks the answer.

Fine-tuning improves style and compliance, but it is not the safety boundary.

## 2. Sanitize The ASK_PENDING Prompt

For `CHAT`, the fast model can receive the user's full text.

For `ASK_PENDING`, do not send the exact factual question as an answer task. Send only a safe, abstracted context:

```text
The user asked a factual geography question.
Another model is checking the answer.
Keep the conversation natural.
Do not name places, countries, capitals, numbers, dates, or factual answers.
```

The fast model can still be interactive, but it should not have a direct path to solve and speak the factual answer.

## 3. Verify Before TTS

Before speaking any fast-model `ASK_PENDING` reply, run a verifier.

The verifier should reject replies that:
- answer the pending question;
- include named entities or facts from the pending topic;
- sound like a final answer;
- contradict or preempt the backend answer.

If the verifier rejects the reply, regenerate once with stricter context or use a safe fallback. The backend factual model remains the only source allowed to speak the final answer.
