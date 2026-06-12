package com.voicedemo.gateway.config;

import com.voicedemo.gateway.ws.BrowserSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final BrowserSocketHandler browserSocketHandler;

    public WebSocketConfig(BrowserSocketHandler browserSocketHandler) {
        this.browserSocketHandler = browserSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(browserSocketHandler, "/ws/voice")
                .setAllowedOrigins("*");
    }
}

