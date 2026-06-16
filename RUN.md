# Run

## 1. Start Ollama

```sh
brew services start ollama
ollama pull qwen2.5:1.5b
```

## 2. Create `.env`

```sh
cp .env.example .env
```

Edit `.env` and set:

```sh
LLM_MODE=real
LLM_BASE_URL=https://api.openai.com/v1
LLM_API_KEY=your_key_here
LLM_MODEL_ROUTER=gpt-4o-mini
LLM_MODEL_ANSWER=gpt-4o-mini
FAST_LLM_BASE_URL=http://127.0.0.1:11434/v1
FAST_LLM_MODEL=qwen2.5:1.5b
```

## 3. Start STT

```sh
cd /Users/riteshrajput/Desktop/MoshiV2/stt-service
/opt/homebrew/bin/python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8081
```

## 4. Start TTS

```sh
cd /Users/riteshrajput/Desktop/MoshiV2/tts-service
/opt/homebrew/bin/python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8082
```

## 5. Start Gateway

```sh
cd /Users/riteshrajput/Desktop/MoshiV2
mvn -pl gateway package
set -a
source .env
set +a
export VOICE_RUNTIME=qwen
export STT_MODE=real
export TTS_MODE=real
export FAST_LLM_MODE=real
java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar
```

## 6. Open App

Open:

```text
http://127.0.0.1:8080
```

Use:

```text
Test Speaker -> Connect -> Start Mic
```

## 7. Check Logs

```sh
python3 scripts/timing_log.py data/events.jsonl --out data/timing-log.md --tail 20
python3 scripts/flow_log.py data/events.jsonl --out data/flow-log.md --tail 20
```

Runtime logs under `data/` are local only and are not committed.
