package com.voicedemo.gateway.ws;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class OggPageWriter {
    private final int serial = ThreadLocalRandom.current().nextInt();
    private int sequence;

    byte[] page(int headerType, long granulePosition, byte[] packet) {
        List<Integer> lacing = lacing(packet.length);
        ByteArrayOutputStream page = new ByteArrayOutputStream(27 + lacing.size() + packet.length);
        page.writeBytes(new byte[] {'O', 'g', 'g', 'S'});
        page.write(0);
        page.write(headerType);
        page.writeBytes(longLe(granulePosition));
        page.writeBytes(intLe(serial));
        page.writeBytes(intLe(sequence++));
        page.writeBytes(new byte[] {0, 0, 0, 0});
        page.write(lacing.size());
        for (Integer segment : lacing) {
            page.write(segment);
        }
        page.writeBytes(packet);

        byte[] bytes = page.toByteArray();
        int checksum = OggCrc.checksum(bytes);
        writeIntLe(bytes, 22, checksum);
        return bytes;
    }

    private List<Integer> lacing(int packetLength) {
        List<Integer> values = new ArrayList<>();
        int remaining = packetLength;
        while (remaining >= 255) {
            values.add(255);
            remaining -= 255;
        }
        values.add(remaining);
        return values;
    }

    private byte[] intLe(int value) {
        return ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }

    private byte[] longLe(long value) {
        return ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
    }

    private void writeIntLe(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
    }
}
