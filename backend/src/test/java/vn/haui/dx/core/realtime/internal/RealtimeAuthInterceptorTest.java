package vn.haui.dx.core.realtime.internal;

import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import vn.haui.dx.core.realtime.api.SubscriptionGuard;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealtimeAuthInterceptorTest {
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final RealtimeSessions sessions = new RealtimeSessions(5);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<SubscriptionGuard> guards = mock(ObjectProvider.class);
    private final RealtimeAuthInterceptor interceptor = new RealtimeAuthInterceptor(decoder, sessions, guards);

    private void connect(Jwt jwt) {
        when(decoder.decode("token")).thenReturn(jwt);
        var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setSessionId("session");
        headers.setNativeHeader("Authorization", "Bearer token");
        headers.setLeaveMutable(true);
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null);
    }

    private Jwt.Builder token() {
        return Jwt.withTokenValue("token").header("alg", "RS256").claim("iss", "issuer");
    }

    @Test void rejectsMissingOrBlankSubjectAndMissingOrExpiredExpiry() {
        Stream.of(
            token().expiresAt(Instant.now().plusSeconds(60)).build(),
            token().subject(" ").expiresAt(Instant.now().plusSeconds(60)).build(),
            token().subject("user").build(),
            token().subject("user").expiresAt(Instant.now().minusSeconds(1)).build()
        ).forEach(jwt -> {
            assertThrows(MessageDeliveryException.class, () -> connect(jwt));
            assertTrue(sessions.expired("session"));
        });
    }

    @Test void acceptsIdentifiedUnexpiredUser() {
        assertDoesNotThrow(() -> connect(token().subject("user").expiresAt(Instant.now().plusSeconds(60)).build()));
        assertFalse(sessions.expired("session"));
    }
}
