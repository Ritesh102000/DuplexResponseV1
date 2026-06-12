package com.voicedemo.gateway.ws;

public record MoshiWireMessage(MoshiMessageType type, byte[] payload) {
}

