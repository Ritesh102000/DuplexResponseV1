package com.voicedemo.gateway.ws;

public enum MoshiMessageType {
    HANDSHAKE(0),
    AUDIO(1),
    TEXT(2),
    CONTROL(3),
    METADATA(4),
    ERROR(5),
    PING(6),
    COLORED_TEXT(7),
    IMAGE(8),
    CODES(9);

    private final int wireValue;

    MoshiMessageType(int wireValue) {
        this.wireValue = wireValue;
    }

    public byte wireValue() {
        return (byte) wireValue;
    }

    public static MoshiMessageType fromWireValue(int value) {
        for (MoshiMessageType type : values()) {
            if (type.wireValue == value) {
                return type;
            }
        }
        return null;
    }
}

