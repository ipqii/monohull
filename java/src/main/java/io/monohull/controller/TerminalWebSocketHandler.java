package io.monohull.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.monohull.service.DockerService;
import io.monohull.service.EnvironmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Bridges a browser xterm to an interactive {@code docker exec} shell inside one
 * of an environment's containers ({@code /api/containers/{id}/terminal}).
 *
 * <p>Wire protocol: client-to-server BINARY frames are raw stdin bytes; TEXT frames
 * are JSON control messages (currently {@code {"type":"resize","cols":C,"rows":R}}).
 * Server-to-client BINARY frames are raw PTY output. The handshake rides the normal
 * session cookie, so Spring Security's {@code /api/**} rule applies unchanged.
 */
@Component
public class TerminalWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TerminalWebSocketHandler.class);
    private static final String TERMINAL_ATTR = "monohull.terminal";
    private static final String DELEGATE_ATTR = "monohull.terminal.session";

    private final EnvironmentService envService;
    private final DockerService dockerService;
    private final ObjectMapper mapper = new ObjectMapper();

    public TerminalWebSocketHandler(EnvironmentService envService, DockerService dockerService) {
        this.envService = envService;
        this.dockerService = dockerService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession rawSession) throws Exception {
        Long containerId = containerIdFromPath(rawSession);
        if (containerId == null) {
            rawSession.close(CloseStatus.BAD_DATA.withReason("Malformed terminal URL"));
            return;
        }

        // Docker pushes output from its own transport thread while control frames arrive on
        // servlet threads; the decorator serializes sends so Tomcat never sees a concurrent write.
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(rawSession, 10_000, 512 * 1024);
        rawSession.getAttributes().put(DELEGATE_ATTR, session);

        String dockerContainerId;
        DockerService.TerminalSession terminal;
        try {
            dockerContainerId = envService.getDockerContainerIdForTerminal(containerId);
            terminal = dockerService.startTerminal(
                dockerContainerId,
                bytes -> sendOutput(session, bytes),
                () -> closeQuietly(session, CloseStatus.NORMAL.withReason("Shell exited")));
        } catch (Exception e) {
            log.warn("Terminal for container {} failed to start: {}", containerId, e.getMessage());
            closeQuietly(session, CloseStatus.SERVER_ERROR.withReason(truncateReason(e.getMessage())));
            return;
        }
        rawSession.getAttributes().put(TERMINAL_ATTR, terminal);
        log.info("Terminal opened on container {} (exec {})", containerId, terminal.execId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        DockerService.TerminalSession terminal = terminalOf(session);
        if (terminal == null) return;
        ByteBuffer payload = message.getPayload();
        byte[] data = new byte[payload.remaining()];
        payload.get(data);
        terminal.write(data);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        DockerService.TerminalSession terminal = terminalOf(session);
        if (terminal == null) return;
        try {
            JsonNode node = mapper.readTree(message.getPayload());
            if ("resize".equals(node.path("type").asText())) {
                int cols = node.path("cols").asInt(0);
                int rows = node.path("rows").asInt(0);
                if (cols > 0 && rows > 0) {
                    dockerService.resizeTerminal(terminal.execId(), rows, cols);
                }
            }
        } catch (IOException e) {
            log.debug("Ignoring malformed terminal control message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        DockerService.TerminalSession terminal =
            (DockerService.TerminalSession) session.getAttributes().remove(TERMINAL_ATTR);
        if (terminal != null) {
            terminal.close();
            log.info("Terminal closed (exec {}, status {})", terminal.execId(), status.getCode());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Terminal transport error: {}", exception.getMessage());
        closeQuietly(sendableSession(session), CloseStatus.SERVER_ERROR);
    }

    private static DockerService.TerminalSession terminalOf(WebSocketSession session) {
        return (DockerService.TerminalSession) session.getAttributes().get(TERMINAL_ATTR);
    }

    /** The decorator wrapping this raw session, if the connection got far enough to create one. */
    private static WebSocketSession sendableSession(WebSocketSession session) {
        Object delegate = session.getAttributes().get(DELEGATE_ATTR);
        return delegate instanceof WebSocketSession s ? s : session;
    }

    private static void sendOutput(WebSocketSession session, byte[] bytes) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new BinaryMessage(bytes));
            }
        } catch (IOException | IllegalStateException e) {
            // Browser went away mid-stream; the close handler tears down the exec.
            log.debug("Dropping terminal output for closed session: {}", e.getMessage());
        }
    }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException e) {
            log.debug("Terminal session close failed: {}", e.getMessage());
        }
    }

    /** Close reasons are capped at 123 UTF-8 bytes by RFC 6455. */
    private static String truncateReason(String message) {
        if (message == null) return "Terminal failed to start";
        return message.length() > 120 ? message.substring(0, 120) : message;
    }

    /** Extract {id} from /api/containers/{id}/terminal. */
    private static Long containerIdFromPath(WebSocketSession session) {
        if (session.getUri() == null) return null;
        String[] parts = session.getUri().getPath().split("/");
        // ["", "api", "containers", "{id}", "terminal"]
        if (parts.length < 5) return null;
        try {
            return Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
