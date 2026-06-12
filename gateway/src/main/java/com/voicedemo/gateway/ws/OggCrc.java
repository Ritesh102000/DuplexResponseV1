package com.voicedemo.gateway.ws;

final class OggCrc {
    private static final int[] TABLE = buildTable();

    private OggCrc() {
    }

    static int checksum(byte[] data) {
        int crc = 0;
        for (byte datum : data) {
            crc = (crc << 8) ^ TABLE[((crc >>> 24) & 0xff) ^ Byte.toUnsignedInt(datum)];
        }
        return crc;
    }

    private static int[] buildTable() {
        int[] table = new int[256];
        for (int i = 0; i < table.length; i++) {
            int value = i << 24;
            for (int bit = 0; bit < 8; bit++) {
                if ((value & 0x80000000) != 0) {
                    value = (value << 1) ^ 0x04c11db7;
                } else {
                    value <<= 1;
                }
            }
            table[i] = value;
        }
        return table;
    }
}
