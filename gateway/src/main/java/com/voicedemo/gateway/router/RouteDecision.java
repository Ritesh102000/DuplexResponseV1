package com.voicedemo.gateway.router;

public record RouteDecision(RouteLabel label, double confidence, String reason) {
}

