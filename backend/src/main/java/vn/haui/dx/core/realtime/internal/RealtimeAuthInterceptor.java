package vn.haui.dx.core.realtime.internal;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.haui.dx.core.realtime.api.RealtimeEvents;
import vn.haui.dx.core.realtime.api.SubscriptionGuard;

/**
 * CONNECT: token trong header Authorization của frame (không qua URL để khỏi lọt vào log).
 * SUBSCRIBE: kiểm quyền từng kênh; không đủ quyền thì bỏ lặng lẽ (không đóng cả kết nối).
 * SEND: client không được gửi gì lên.
 */
class RealtimeAuthInterceptor implements ChannelInterceptor {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RealtimeAuthInterceptor.class);
    private static final String USER_EVENTS = "/user" + RealtimeEvents.USER_QUEUE;

    private final JwtDecoder decoder;
    private final RealtimeSessions sessions;
    // Lấy lười: guard phụ thuộc dịch vụ nghiệp vụ, dịch vụ lại phát sự kiện qua broker của cấu hình này
    private final ObjectProvider<SubscriptionGuard> guards;

    RealtimeAuthInterceptor(JwtDecoder decoder, RealtimeSessions sessions, ObjectProvider<SubscriptionGuard> guards) {
        this.decoder = decoder; this.sessions = sessions; this.guards = guards;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;
        if (accessor.getCommand() == StompCommand.CONNECT) connect(accessor);
        else if (accessor.getCommand() == StompCommand.SUBSCRIBE) return subscribe(accessor) ? message : null;
        else if (accessor.getCommand() == StompCommand.SEND) throw new MessageDeliveryException("Client messages are not accepted");
        return message;
    }

    private void connect(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) throw new MessageDeliveryException("Authentication required");
        try {
            var jwt = decoder.decode(header.substring(7));
            if (!sessions.register(accessor.getSessionId(), jwt.getSubject(), jwt.getExpiresAt()))
                throw new MessageDeliveryException("Too many realtime connections");
            accessor.setUser(new JwtAuthenticationToken(jwt));
        } catch (JwtException failure) {
            throw new MessageDeliveryException("Authentication required");
        }
    }

    private boolean subscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (!(accessor.getUser() instanceof JwtAuthenticationToken user) || destination == null
                || sessions.expired(accessor.getSessionId())) return false;
        if (USER_EVENTS.equals(destination)) return true;
        if (!destination.startsWith("/topic/")) return false;
        var previous = SecurityContextHolder.getContext();
        try {
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(user);
            SecurityContextHolder.setContext(context);
            var guard = guards.orderedStream().filter(candidate -> candidate.supports(destination)).findFirst();
            return guard.isPresent() && guard.get().allows(destination);
        } catch (RuntimeException failure) {
            LOG.debug("Realtime subscription {} denied: {}", destination, failure.getMessage());
            return false;
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
