package vn.haui.dx.core.realtime.internal;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import vn.haui.dx.core.realtime.api.SubscriptionGuard;

/** STOMP trên WebSocket ở /ws, broker trong bộ nhớ (một instance platform); chỉ chiều server → client. */
@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(name = "dx.realtime.enabled", havingValue = "true", matchIfMissing = true)
class RealtimeConfig implements WebSocketMessageBrokerConfigurer {
    private final RealtimeSessions sessions;
    private final RealtimeAuthInterceptor auth;
    private final List<String> origins;
    private final TaskScheduler brokerScheduler;

    RealtimeConfig(JwtDecoder decoder, ObjectProvider<SubscriptionGuard> guards,
                   @Value("${dx.realtime.allowed-origins:http://localhost:5173}") List<String> origins,
                   @Value("${dx.realtime.max-connections-per-user:5}") int maxPerUser,
                   @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler brokerScheduler) {
        this.sessions = new RealtimeSessions(maxPerUser);
        this.auth = new RealtimeAuthInterceptor(decoder, sessions, guards);
        // Bỏ phần tử rỗng (biến môi trường thêm origin để trống)
        this.origins = origins.stream().map(String::strip).filter(origin -> !origin.isEmpty()).distinct().toList();
        this.brokerScheduler = brokerScheduler;
    }

    /** Bean để @Scheduled đóng phiên hết hạn chạy được. */
    @Bean
    RealtimeSessions realtimeSessions() { return sessions; }

    /**
     * Broker tự tạo messageBrokerTaskScheduler nên Spring Boot thôi tạo taskScheduler mặc định;
     * khai báo lại để các job @Scheduled vẫn chạy trên scheduler cũ (cấu hình spring.task.scheduling.*).
     */
    @Bean
    ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) { return builder.build(); }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(origins.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(new long[] {10_000, 10_000})
            .setTaskScheduler(brokerScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(auth);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(16 * 1024)
            .setSendBufferSizeLimit(512 * 1024)
            .setSendTimeLimit(10_000)
            .addDecoratorFactory(sessions::decorate);
    }
}
