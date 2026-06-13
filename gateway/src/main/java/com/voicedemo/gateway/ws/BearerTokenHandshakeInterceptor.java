package com.voicedemo.gateway.ws;

import com.voicedemo.gateway.config.ModeProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
public class BearerTokenHandshakeInterceptor implements HandshakeInterceptor {
    private final ModeProperties properties;

    public BearerTokenHandshakeInterceptor(ModeProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String expectedToken = properties.wsBearerToken();
        if (expectedToken == null || expectedToken.isBlank()) {
            return true;
        }
        if (expectedToken.equals(headerBearerToken(request.getHeaders()))
                || expectedToken.equals(queryToken(request.getURI()))) {
            return true;
        }
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
    }

    private String headerBearerToken(HttpHeaders headers) {
        List<String> values = headers.get(HttpHeaders.AUTHORIZATION);
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && value.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return value.substring(7).trim();
            }
        }
        return "";
    }

    private String queryToken(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return "";
        }
        String token = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("token");
        return token == null ? "" : token;
    }
}
