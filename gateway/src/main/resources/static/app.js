const connectButton = document.querySelector("#connect");
const disconnectButton = document.querySelector("#disconnect");
const toneButton = document.querySelector("#tone");
const statusEl = document.querySelector("#status");
const debugEl = document.querySelector("#debug");

let socket;
let audioContext;

function log(line) {
  debugEl.textContent += `${new Date().toISOString()} ${line}\n`;
  debugEl.scrollTop = debugEl.scrollHeight;
}

function setConnected(connected) {
  connectButton.disabled = connected;
  disconnectButton.disabled = !connected;
  toneButton.disabled = !connected;
  statusEl.textContent = connected ? "Connected" : "Disconnected";
}

function pcmSineFrame() {
  const sampleRate = 24000;
  const samples = Math.floor(sampleRate * 0.08);
  const bytes = new ArrayBuffer(samples * 2);
  const view = new DataView(bytes);
  for (let i = 0; i < samples; i += 1) {
    const value = Math.sin((i / sampleRate) * 440 * Math.PI * 2) * 0.18;
    view.setInt16(i * 2, Math.max(-32768, Math.min(32767, value * 32767)), true);
  }
  return bytes;
}

function playPcm(arrayBuffer) {
  audioContext ||= new AudioContext({ sampleRate: 24000 });
  const pcm = new Int16Array(arrayBuffer);
  const buffer = audioContext.createBuffer(1, pcm.length, 24000);
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
  socket?.close();
});

toneButton.addEventListener("click", () => {
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    return;
  }
  socket.send(pcmSineFrame());
  log("sent 80ms test PCM frame");
});

