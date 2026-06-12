package com.voicedemo.gateway.ws;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class OggPacketReader {
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private ByteArrayOutputStream currentPacket = new ByteArrayOutputStream();

    synchronized List<byte[]> append(byte[] bytes) {
        pending.writeBytes(bytes);
        List<byte[]> packets = new ArrayList<>();
        byte[] data = pending.toByteArray();
        int offset = 0;

        while (true) {
            int capture = indexOfCapture(data, offset);
            if (capture < 0) {
                retain(data, Math.max(0, data.length - 3));
                return packets;
            }
            if (capture > offset) {
                offset = capture;
            }
            if (data.length - offset < 27) {
                retain(data, offset);
                return packets;
            }

            int pageSegments = Byte.toUnsignedInt(data[offset + 26]);
            if (data.length - offset < 27 + pageSegments) {
                retain(data, offset);
                return packets;
            }

            int segmentTableOffset = offset + 27;
            int payloadLength = 0;
            for (int i = 0; i < pageSegments; i++) {
                payloadLength += Byte.toUnsignedInt(data[segmentTableOffset + i]);
            }
            int pageLength = 27 + pageSegments + payloadLength;
            if (data.length - offset < pageLength) {
                retain(data, offset);
                return packets;
            }

            boolean continued = (data[offset + 5] & 0x01) != 0;
            if (!continued && currentPacket.size() > 0) {
                currentPacket.reset();
            }

            int payloadOffset = segmentTableOffset + pageSegments;
            int cursor = payloadOffset;
            for (int i = 0; i < pageSegments; i++) {
                int segmentLength = Byte.toUnsignedInt(data[segmentTableOffset + i]);
                currentPacket.writeBytes(Arrays.copyOfRange(data, cursor, cursor + segmentLength));
                cursor += segmentLength;
                if (segmentLength < 255) {
                    packets.add(currentPacket.toByteArray());
                    currentPacket = new ByteArrayOutputStream();
                }
            }

            offset += pageLength;
            if (offset >= data.length) {
                retain(data, offset);
                return packets;
            }
        }
    }

    private int indexOfCapture(byte[] data, int from) {
        for (int i = from; i <= data.length - 4; i++) {
            if (data[i] == 'O' && data[i + 1] == 'g' && data[i + 2] == 'g' && data[i + 3] == 'S') {
                return i;
            }
        }
        return -1;
    }

    private void retain(byte[] data, int from) {
        pending.reset();
        if (from < data.length) {
            pending.writeBytes(Arrays.copyOfRange(data, from, data.length));
        }
    }
}
