package io.monohull.config;

import io.monohull.controller.TerminalWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the container-terminal websocket endpoint. Origins are left at the
 * default (same-origin only): the terminal executes shells as root inside managed
 * containers, and auth rides the session cookie, so cross-site handshakes must
 * stay rejected. The Vite dev server proxies the handshake with a rewritten
 * Origin header (see frontend/vite.config.ts) to satisfy this in development.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TerminalWebSocketHandler terminalHandler;

    public WebSocketConfig(TerminalWebSocketHandler terminalHandler) {
        this.terminalHandler = terminalHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalHandler, "/api/containers/*/terminal");
    }
}
