package com.voicedemo.gateway.config;

import com.voicedemo.gateway.ws.BrowserSocketHandler;
import com.voicedemo.gateway.ws.BearerTokenHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final BrowserSocketHandler browserSocketHandler;
    private final BearerTokenHandshakeInterceptor bearerTokenHandshakeInterceptor;

    public WebSocketConfig(
            BrowserSocketHandler browserSocketHandler,
            BearerTokenHandshakeInterceptor bearerTokenHandshakeInterceptor) {
        this.browserSocketHandler = browserSocketHandler;
        this.bearerTokenHandshakeInterceptor = bearerTokenHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(browserSocketHandler, "/ws/voice")
                .addInterceptors(bearerTokenHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
