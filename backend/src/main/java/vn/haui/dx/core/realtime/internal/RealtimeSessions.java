package vn.haui.dx.core.realtime.internal;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/** Phiên WebSocket đang mở: giới hạn số kết nối mỗi người, đóng phiên khi token hết hạn (client nối lại bằng token mới). */
class RealtimeSessions {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RealtimeSessions.class);
    // Mã đóng riêng (4000–4999): client hiểu là cần nối lại với token mới
    static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4001, "Token expired");

    private record Owner(String subject, Instant expiresAt) {}

    private final int maxPerUser;
    private final Map<String, WebSocketSession> open = new ConcurrentHashMap<>();
    private final Map<String, Owner> owners = new ConcurrentHashMap<>();

    RealtimeSessions(int maxPerUser) { this.maxPerUser = maxPerUser; }

    WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                open.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }
            @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                open.remove(session.getId());
                owners.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }

    /** CONNECT hợp lệ; false khi người này đã đủ số kết nối. */
    synchronized boolean register(String sessionId, String subject, Instant expiresAt) {
        long count = owners.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(sessionId) && entry.getValue().subject().equals(subject)).count();
        if (count >= maxPerUser) return false;
        owners.put(sessionId, new Owner(subject, expiresAt));
        return true;
    }

    boolean expired(String sessionId) {
        var owner = owners.get(sessionId);
        return owner == null || owner.expiresAt() != null && !owner.expiresAt().isAfter(Instant.now());
    }

    @Scheduled(fixedDelayString = "${dx.realtime.expiry-check-ms:30000}")
    void closeExpired() {
        owners.keySet().stream().filter(this::expired).toList().forEach(id -> {
            var session = open.get(id);
            owners.remove(id);
            if (session == null) return;
            try { session.close(TOKEN_EXPIRED); }
            catch (IOException failure) { LOG.debug("Closing expired realtime session failed", failure); }
        });
    }
}
