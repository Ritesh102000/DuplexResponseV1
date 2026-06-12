const connectButton = document.querySelector("#connect");
const disconnectButton = document.querySelector("#disconnect");
const startMicButton = document.querySelector("#startMic");
const stopMicButton = document.querySelector("#stopMic");
const speakerTestButton = document.querySelector("#speakerTest");
const toneButton = document.querySelector("#tone");
const sendUtteranceButton = document.querySelector("#sendUtterance");
const utteranceInput = document.querySelector("#utterance");
const statusEl = document.querySelector("#status");
const debugEl = document.querySelector("#debug");

let socket;
let audioContext;
let micStream;
let micSource;
let micNode;
let silenceNode;
let micRunning = false;
let captureBuffer = [];
let resampleBuffer = new Float32Array(0);
let resamplePosition = 0;
let outputPlaybackTime = 0;
let outputSources = new Set();

const TARGET_SAMPLE_RATE = 24000;
const BROWSER_FRAME_SAMPLES = Math.floor(TARGET_SAMPLE_RATE * 0.08);
const OUTPUT_GAIN = 6;

function log(line) {
  debugEl.textContent += `${new Date().toISOString()} ${line}\n`;
  debugEl.scrollTop = debugEl.scrollHeight;
}

function setConnected(connected) {
  connectButton.disabled = connected;
  disconnectButton.disabled = !connected;
  startMicButton.disabled = !connected || micRunning;
  stopMicButton.disabled = !micRunning;
  toneButton.disabled = !connected;
  sendUtteranceButton.disabled = !connected;
  statusEl.textContent = connected ? "Connected" : "Disconnected";
}

async function unlockAudio() {
  audioContext ||= new AudioContext({ sampleRate: TARGET_SAMPLE_RATE });
  if (audioContext.state === "suspended") {
    await audioContext.resume();
  }
  return audioContext;
}

function pcmSineFrame() {
  const sampleRate = TARGET_SAMPLE_RATE;
  const samples = BROWSER_FRAME_SAMPLES;
  const bytes = new ArrayBuffer(samples * 2);
  const view = new DataView(bytes);
  for (let i = 0; i < samples; i += 1) {
    const value = Math.sin((i / sampleRate) * 440 * Math.PI * 2) * 0.18;
    view.setInt16(i * 2, Math.max(-32768, Math.min(32767, value * 32767)), true);
  }
  return bytes;
}

async function playPcm(arrayBuffer) {
  const context = await unlockAudio();
  const pcm = pcmBytesToInt16(arrayBuffer);
  const buffer = audioContext.createBuffer(1, pcm.length, TARGET_SAMPLE_RATE);
  const channel = buffer.getChannelData(0);
  let inputPeak = 0;
  let outputPeak = 0;
  for (let i = 0; i < pcm.length; i += 1) {
    const value = pcm[i] / 32768;
    const boosted = Math.max(-1, Math.min(1, value * OUTPUT_GAIN));
    inputPeak = Math.max(inputPeak, Math.abs(value));
    outputPeak = Math.max(outputPeak, Math.abs(boosted));
    channel[i] = boosted;
  }
  const source = audioContext.createBufferSource();
  source.buffer = buffer;
  source.connect(audioContext.destination);
  const startAt = Math.max(context.currentTime + 0.03, outputPlaybackTime);
  outputPlaybackTime = startAt + buffer.duration;
  outputSources.add(source);
  source.addEventListener("ended", () => {
    outputSources.delete(source);
    if (outputSources.size === 0 && outputPlaybackTime < context.currentTime) {
      outputPlaybackTime = 0;
    }
  });
  source.start(startAt);
  return {
    samples: pcm.length,
    inputPeak,
    outputPeak,
    queuedMs: Math.max(0, Math.round((startAt - context.currentTime) * 1000)),
  };
}

function pcmBytesToInt16(arrayBuffer) {
  const view = new DataView(arrayBuffer);
  const pcm = new Int16Array(Math.floor(view.byteLength / 2));
  for (let i = 0; i < pcm.length; i += 1) {
    pcm[i] = view.getInt16(i * 2, true);
  }
  return pcm;
}

async function playSpeakerTest() {
  const context = await unlockAudio();
  const samples = Math.floor(TARGET_SAMPLE_RATE * 0.35);
  const buffer = context.createBuffer(1, samples, TARGET_SAMPLE_RATE);
  const channel = buffer.getChannelData(0);
  for (let i = 0; i < samples; i += 1) {
    const fade = Math.min(i / 400, (samples - i) / 400, 1);
    channel[i] = Math.sin((i / TARGET_SAMPLE_RATE) * 660 * Math.PI * 2) * 0.18 * Math.max(0, fade);
  }
  const source = context.createBufferSource();
  source.buffer = buffer;
  source.connect(context.destination);
  source.start();
}

connectButton.addEventListener("click", () => {
  unlockAudio().catch((error) => log(`audio unlock error ${error.message}`));
  resetOutputQueue();
  const sessionId = crypto.randomUUID();
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  socket = new WebSocket(`${protocol}://${window.location.host}/ws/voice?sessionId=${sessionId}`);
  socket.binaryType = "arraybuffer";

  socket.addEventListener("open", () => {
    setConnected(true);
    log(`ws open sessionId=${sessionId}`);
  });
  socket.addEventListener("close", () => {
    stopMic();
    resetOutputQueue();
    setConnected(false);
    log("ws close");
  });
  socket.addEventListener("message", async (event) => {
    if (typeof event.data === "string") {
      log(event.data);
      return;
    }
    try {
      const stats = await playPcm(event.data);
      log(`audio ${event.data.byteLength} bytes samples=${stats.samples} peak=${stats.inputPeak.toFixed(3)} out=${stats.outputPeak.toFixed(3)} queued=${stats.queuedMs}ms`);
    } catch (error) {
      log(`audio playback error ${error.message}`);
    }
  });
});

disconnectButton.addEventListener("click", () => {
  stopMic();
  socket?.close();
});

startMicButton.addEventListener("click", async () => {
  if (!socket || socket.readyState !== WebSocket.OPEN || micRunning) {
    return;
  }
  try {
    await unlockAudio();
    await audioContext.audioWorklet.addModule("/mic-capture-worklet.js");
    micStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
    });
    captureBuffer = [];
    resampleBuffer = new Float32Array(0);
    resamplePosition = 0;
    micSource = audioContext.createMediaStreamSource(micStream);
    micNode = new AudioWorkletNode(audioContext, "mic-capture");
    silenceNode = audioContext.createGain();
    silenceNode.gain.value = 0;
    micNode.port.onmessage = (event) => {
      sendCapturedAudio(event.data, audioContext.sampleRate);
    };
    micSource.connect(micNode);
    micNode.connect(silenceNode);
    silenceNode.connect(audioContext.destination);
    micRunning = true;
    setConnected(true);
    log(`mic started sourceRate=${audioContext.sampleRate}`);
  } catch (error) {
    log(`mic error ${error.message}`);
    stopMic();
  }
});

stopMicButton.addEventListener("click", () => {
  stopMic();
});

speakerTestButton.addEventListener("click", () => {
  playSpeakerTest()
    .then(() => log("speaker test played"))
    .catch((error) => log(`speaker test error ${error.message}`));
});

toneButton.addEventListener("click", () => {
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    return;
  }
  socket.send(pcmSineFrame());
  log("sent 80ms test PCM frame");
});

sendUtteranceButton.addEventListener("click", () => {
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    return;
  }
  const payload = {
    type: "transcript.user",
    text: utteranceInput.value,
    ts: Date.now(),
  };
  socket.send(JSON.stringify(payload));
  log(`sent ${JSON.stringify(payload)}`);
});

function sendCapturedAudio(input, sourceRate) {
  if (!socket || socket.readyState !== WebSocket.OPEN || !micRunning) {
    return;
  }
  const samples = resampleToTarget(input, sourceRate);
  for (let i = 0; i < samples.length; i += 1) {
    captureBuffer.push(samples[i]);
  }
  while (captureBuffer.length >= BROWSER_FRAME_SAMPLES) {
    const frame = captureBuffer.splice(0, BROWSER_FRAME_SAMPLES);
    socket.send(floatToPcmBytes(frame));
  }
}

function resampleToTarget(input, sourceRate) {
  const combined = new Float32Array(resampleBuffer.length + input.length);
  combined.set(resampleBuffer, 0);
  combined.set(input, resampleBuffer.length);

  const ratio = sourceRate / TARGET_SAMPLE_RATE;
  const output = [];
  while (resamplePosition + 1 < combined.length) {
    const leftIndex = Math.floor(resamplePosition);
    const rightIndex = leftIndex + 1;
    const mix = resamplePosition - leftIndex;
    output.push(combined[leftIndex] * (1 - mix) + combined[rightIndex] * mix);
    resamplePosition += ratio;
  }

  const consumed = Math.floor(resamplePosition);
  resampleBuffer = combined.slice(consumed);
  resamplePosition -= consumed;
  return output;
}

function floatToPcmBytes(samples) {
  const bytes = new ArrayBuffer(samples.length * 2);
  const view = new DataView(bytes);
  for (let i = 0; i < samples.length; i += 1) {
    const sample = Math.max(-1, Math.min(1, samples[i]));
    view.setInt16(i * 2, sample < 0 ? sample * 32768 : sample * 32767, true);
  }
  return bytes;
}

function stopMic() {
  if (!micRunning && !micStream) {
    return;
  }
  micRunning = false;
  micNode?.disconnect();
  micSource?.disconnect();
  silenceNode?.disconnect();
  micNode = undefined;
  micSource = undefined;
  silenceNode = undefined;
  micStream?.getTracks().forEach((track) => track.stop());
  micStream = undefined;
  captureBuffer = [];
  resampleBuffer = new Float32Array(0);
  resamplePosition = 0;
  setConnected(socket?.readyState === WebSocket.OPEN);
  log("mic stopped");
}

function resetOutputQueue() {
  outputSources.forEach((source) => {
    try {
      source.stop();
    } catch (error) {
      // Source may already have ended.
    }
  });
  outputSources.clear();
  outputPlaybackTime = 0;
}
