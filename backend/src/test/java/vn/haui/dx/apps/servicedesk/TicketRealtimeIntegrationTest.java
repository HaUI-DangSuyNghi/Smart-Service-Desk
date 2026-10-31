package vn.haui.dx.apps.servicedesk;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** WebSocket thật trên cổng ngẫu nhiên: token ở frame CONNECT, quyền từng kênh, sự kiện chỉ phát sau commit. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"serviceDesk.confirmation.scheduled=false", "dx.realtime.max-connections-per-user=2"})
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class TicketRealtimeIntegrationTest {
    private static final String WORKSPACE = "lab-a";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    // Token giả "subject:role" → JWT có group /workspaces/lab-a/role
    @MockBean JwtDecoder decoder;

    private final RestTemplate http = new RestTemplate();

    private void tokens() {
        when(decoder.decode(anyString())).thenAnswer(call -> {
            String token = call.getArgument(0);
            if (!token.contains(":")) throw new org.springframework.security.oauth2.jwt.BadJwtException("Invalid token");
            String[] parts = token.split(":", 2);
            return Jwt.withTokenValue(token).header("alg", "none").subject(parts[0])
                .claim("groups", List.of("/workspaces/" + WORKSPACE + "/" + parts[1]))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        });
    }

    private StompSession connect(String token) throws Exception {
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        var headers = new StompHeaders();
        if (token != null) headers.add("Authorization", "Bearer " + token);
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
            new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<Map<String, Object>> listen(StompSession session, String destination) throws Exception {
        BlockingQueue<Map<String, Object>> events = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @SuppressWarnings("unchecked")
            @Override public void handleFrame(StompHeaders headers, Object payload) { events.add((Map<String, Object>) payload); }
        });
        // SUBSCRIBE không có xác nhận: chờ broker ghi nhận trước khi tạo thay đổi
        Thread.sleep(300);
        return events;
    }

    private JsonNode post(String token, String path, Object body) throws Exception {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = http.exchange("http://localhost:" + port + path, HttpMethod.POST,
            new HttpEntity<>(json.writeValueAsString(body), headers), String.class);
        return json.readTree(response.getBody());
    }

    private JsonNode createTicket(String owner) throws Exception {
        return post(owner + ":requester", "/api/apps/servicedesk/v1/workspaces/" + WORKSPACE + "/tickets",
            Map.of("clientRequestId", UUID.randomUUID(), "title", "Realtime " + UUID.randomUUID(),
                "description", "Synthetic support request"));
    }

    private static Map<String, Object> next(BlockingQueue<Map<String, Object>> events, String type) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var event = events.poll(200, TimeUnit.MILLISECONDS);
            if (event != null && type.equals(event.get("type"))) return event;
        }
        return null;
    }

    @Test void connectRequiresBearerTokenInStompFrame() {
        tokens();
        // Server trả ERROR rồi đóng kết nối: client không bao giờ nhận CONNECTED
        assertThrows(Exception.class, () -> connect(null));
        assertThrows(Exception.class, () -> connect("bad-token"));
    }

    @Test void ticketEventsReachOnlyAllowedChannelsAfterCommit() throws Exception {
        tokens();
        String owner = "rt-owner-" + UUID.randomUUID(), other = "rt-other-" + UUID.randomUUID();
        var coordinator = connect("rt-coordinator:coordinator");
        var requester = connect(owner + ":requester");
        var stranger = connect(other + ":requester");
        var workspaceFeed = listen(coordinator, "/topic/workspaces/" + WORKSPACE + "/tickets");
        var ownerFeed = listen(requester, "/user/queue/events");
        // Người gửi không được nghe kênh tổng: đăng ký bị bỏ lặng lẽ, kết nối vẫn sống
        var forbiddenFeed = listen(stranger, "/topic/workspaces/" + WORKSPACE + "/tickets");

        var ticket = createTicket(owner);
        String id = ticket.path("id").asText();
        assertNotNull(next(workspaceFeed, "TICKET_CHANGED"));
        var mine = next(ownerFeed, "TICKET_CHANGED");
        assertNotNull(mine);
        assertEquals(id, mine.get("id"));
        assertNull(forbiddenFeed.poll(1, TimeUnit.SECONDS));
        assertTrue(stranger.isConnected());

        // Kênh của ticket: chủ ticket nghe được, người gửi khác thì không
        var detailFeed = listen(requester, "/topic/workspaces/" + WORKSPACE + "/tickets/" + id);
        var strangerDetail = listen(stranger, "/topic/workspaces/" + WORKSPACE + "/tickets/" + id);
        post("rt-coordinator:coordinator", "/api/apps/servicedesk/v1/workspaces/" + WORKSPACE + "/tickets/" + id + "/comments",
            Map.of("body", "Đã nhận"));
        assertNotNull(next(detailFeed, "TICKET_COMMENTED"));
        assertNull(strangerDetail.poll(1, TimeUnit.SECONDS));

        // Bình luận rỗng bị từ chối (rollback): không có sự kiện
        assertThrows(Exception.class, () -> post(owner + ":requester",
            "/api/apps/servicedesk/v1/workspaces/" + WORKSPACE + "/tickets/" + id + "/comments", Map.of("body", " ")));
        assertNull(next(detailFeed, "TICKET_COMMENTED"));
    }

    @Test void connectionsPerUserAreLimited() throws Exception {
        tokens();
        String subject = "rt-limit-" + UUID.randomUUID();
        connect(subject + ":agent");
        connect(subject + ":agent");
        assertThrows(Exception.class, () -> connect(subject + ":agent"));
    }
}
