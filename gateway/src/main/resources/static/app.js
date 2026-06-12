const connectButton = document.querySelector("#connect");
const disconnectButton = document.querySelector("#disconnect");
const startMicButton = document.querySelector("#startMic");
const stopMicButton = document.querySelector("#stopMic");
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

const TARGET_SAMPLE_RATE = 24000;
const BROWSER_FRAME_SAMPLES = Math.floor(TARGET_SAMPLE_RATE * 0.08);

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

function playPcm(arrayBuffer) {
  audioContext ||= new AudioContext({ sampleRate: TARGET_SAMPLE_RATE });
  const pcm = new Int16Array(arrayBuffer);
  const buffer = audioContext.createBuffer(1, pcm.length, TARGET_SAMPLE_RATE);
  const channel = buffer.getChannelData(0);
  for (let i = 0; i < pcm.length; i += 1) {
    channel[i] = pcm[i] / 32768;
  }
  const source = audioContext.createBufferSource();
  source.buffer = buffer;
  source.connect(audioContext.destination);
  source.start();
}

connectButton.addEventListener("click", () => {
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
    setConnected(false);
    log("ws close");
  });
  socket.addEventListener("message", (event) => {
    if (typeof event.data === "string") {
      log(event.data);
      return;
    }
    log(`audio ${event.data.byteLength} bytes`);
    playPcm(event.data);
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
    audioContext ||= new AudioContext({ sampleRate: TARGET_SAMPLE_RATE });
    await audioContext.resume();
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
