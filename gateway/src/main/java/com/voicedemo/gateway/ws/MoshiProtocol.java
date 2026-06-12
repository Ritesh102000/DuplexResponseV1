package com.voicedemo.gateway.ws;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class MoshiProtocol {
    private MoshiProtocol() {
    }

    public static byte[] handshake() {
        return new byte[] {MoshiMessageType.HANDSHAKE.wireValue(), 0, 0, 0, 0, 0, 0, 0, 0};
    }

    public static byte[] audio(byte[] oggOpusPayload) {
        return encode(MoshiMessageType.AUDIO, oggOpusPayload);
    }

    public static byte[] text(String text) {
        return encode(MoshiMessageType.TEXT, text.getBytes(StandardCharsets.UTF_8));
    }

    public static MoshiWireMessage decode(byte[] frame) {
        if (frame.length == 0) {
            return null;
        }
        MoshiMessageType type = MoshiMessageType.fromWireValue(Byte.toUnsignedInt(frame[0]));
        if (type == null) {
            return null;
        }
        return new MoshiWireMessage(type, Arrays.copyOfRange(frame, 1, frame.length));
    }

    private static byte[] encode(MoshiMessageType type, byte[] payload) {
        byte[] frame = new byte[payload.length + 1];
        frame[0] = type.wireValue();
        System.arraycopy(payload, 0, frame, 1, payload.length);
        return frame;
    }
}

