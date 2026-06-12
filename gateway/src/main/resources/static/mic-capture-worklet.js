class MicCaptureProcessor extends AudioWorkletProcessor {
  process(inputs) {
    const input = inputs[0]?.[0];
    if (input) {
      this.port.postMessage(input.slice(0));
    }
    return true;
  }
}

registerProcessor("mic-capture", MicCaptureProcessor);
