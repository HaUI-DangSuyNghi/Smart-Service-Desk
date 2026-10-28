package vn.haui.dx.core.identity.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeycloakFreshAdminAuthorizationTest {
    private record Reply(int status, String body, int delayMillis) {
        Reply(int status, String body) { this(status, body, 0); }
    }
    private final ConcurrentLinkedQueue<Reply> replies = new ConcurrentLinkedQueue<>();
    private final List<String> requests = java.util.Collections.synchronizedList(new ArrayList<>());
    private final WorkspaceAccess access = mock(WorkspaceAccess.class);
    private final CurrentUser user = mock(CurrentUser.class);
    private HttpServer server;
    private String base;

    @BeforeEach void start() throws Exception {
        when(access.current()).thenReturn(user);
        when(user.sub()).thenReturn("caller");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().toASCIIString()
                + " " + exchange.getRequestHeaders().getFirst("Authorization") + " "
                + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Reply reply = replies.poll();
            if (reply == null) reply = new Reply(500, "unexpected request");
            try {
                Thread.sleep(reply.delayMillis());
                byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(reply.status(), body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally { exchange.close(); }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach void stop() { server.stop(0); SecurityContextHolder.clearContext(); }

    private KeycloakFreshAdminAuthorization guard(Duration budget) {
        return new KeycloakFreshAdminAuthorization(access, new ObjectMapper(),
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
            base, "dx-lab", "dx-directory", "secret&=value", budget);
    }

    private void token() { replies.add(new Reply(200, "{\"access_token\":\"directory-token\"}")); }
    private void account() { replies.add(new Reply(200, "{\"id\":\"caller\",\"username\":\"caller\",\"enabled\":true}")); }
    private void admin() { token(); account(); replies.add(new Reply(200, "[{\"name\":\"platform-admin\"}]")); }
    private void granted() { admin(); replies.add(new Reply(200, "[{\"id\":\"caller\"}]")); }

    @Test void readsAuthenticatedSubjectAndEffectiveRolesNotSignedAdminClaim() {
        granted();
        assertEquals("caller", guard(Duration.ofSeconds(5)).requirePlatformAdmin());
        assertTrue(requests.get(0).contains("client_id=dx-directory&client_secret=secret%26%3Dvalue"));
        assertTrue(requests.get(1).contains("GET /admin/realms/dx-lab/users/caller Bearer directory-token"));
        assertTrue(requests.get(2).contains("/role-mappings/realm/composite?briefRepresentation=true"));
        assertTrue(requests.get(3).contains("/users?search=%22caller%22&max=2&briefRepresentation=true"));
        verify(user, never()).platformAdmin();
        verify(access, never()).requireMember(anyString());
    }

    @Test void neverCachesAccountRoleOrCredentialAndDeniesRevokedRole() {
        var guard = guard(Duration.ofSeconds(5));
        granted();
        assertEquals("caller", guard.requirePlatformAdmin());
        token(); account(); replies.add(new Reply(200, "[]"));
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CoreException.class, guard::requirePlatformAdmin).code());
        assertEquals(7, requests.size());
    }

    static Stream<Arguments> humanFailures() {
        return Stream.of(
            Arguments.of(200, "[]", ErrorCode.FORBIDDEN),
            Arguments.of(200, "[{\"id\":\"another-human\"}]", ErrorCode.FORBIDDEN),
            Arguments.of(200, "{}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of(200, "[{}]", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of(200, "not-json", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of(403, "{}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of(404, "{}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of(500, "{}", ErrorCode.SERVICE_UNAVAILABLE));
    }

    @ParameterizedTest @MethodSource("humanFailures")
    void adminRoleWithoutLiveHumanProofCannotAuthorize(int status, String body, ErrorCode expected) {
        admin();
        replies.add(new Reply(status, body));
        assertEquals(expected, assertThrows(CoreException.class,
            () -> guard(Duration.ofSeconds(5)).requirePlatformAdmin()).code());
    }

    @Test void humanProofIsFreshEvenWhenRoleRemainsAdmin() {
        var guard = guard(Duration.ofSeconds(5));
        granted();
        assertEquals("caller", guard.requirePlatformAdmin());
        admin();
        replies.add(new Reply(200, "[]"));
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CoreException.class, guard::requirePlatformAdmin).code());
        assertEquals(8, requests.size());
    }

    @Test void humanProofSharesTotalTimeoutBudget() {
        admin();
        replies.add(new Reply(200, "[{\"id\":\"caller\"}]", 700));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, assertThrows(CoreException.class,
            () -> guard(Duration.ofMillis(500)).requirePlatformAdmin()).code());
    }

    static Stream<Arguments> failures() {
        return Stream.of(
            Arguments.of("token", 401, "{}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("token", 200, "{\"access_token\":4}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("token", 200, "{}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("account", 404, "{}", ErrorCode.FORBIDDEN),
            Arguments.of("account", 403, "{}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("account", 401, "{}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("account", 500, "{}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("account", 200, "{\"id\":\"other\",\"enabled\":true}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("account", 200, "{\"id\":\"caller\"}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("account", 200, "{\"id\":\"caller\",\"enabled\":false}", ErrorCode.FORBIDDEN),
            Arguments.of("account", 200, "{\"id\":\"caller\",\"enabled\":true,\"serviceAccountClientId\":\"client\"}", ErrorCode.FORBIDDEN),
            Arguments.of("roles", 404, "{}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("roles", 403, "{}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("roles", 500, "{}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("roles", 429, "{}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("roles", 302, "{}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("roles", 200, "not-json", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("roles", 200, "[{\"name\":\"platform-admin\"}] {}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("roles", 200, "[{\"name\":\"platform-admin\",\"clientRole\":true}]", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("account", 200, "{\"id\":\"caller\",\"enabled\":false,\"enabled\":true}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("roles", 200, "{}", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("roles", 200, "[{\"name\":\"platform-admin\"},{}]", ErrorCode.SERVICE_UNAVAILABLE),
            Arguments.of("roles", 200, "[{\"name\":\"coordinator\"}]", ErrorCode.FORBIDDEN));
    }

    @ParameterizedTest @MethodSource("failures")
    void failsClosed(String stage, int status, String body, ErrorCode expected) {
        if (!stage.equals("token")) token();
        if (stage.equals("roles")) account();
        replies.add(new Reply(status, body));
        var error = assertThrows(CoreException.class, () -> guard(Duration.ofSeconds(5)).requirePlatformAdmin());
        assertEquals(expected, error.code());
        assertFalse(error.getMessage().contains("directory-token"));
        assertFalse(error.getMessage().contains("secret"));
        assertTrue(replies.isEmpty());
    }

    @Test void totalBudgetIncludesTokenAndAccountCalls() {
        replies.add(new Reply(200, "{\"access_token\":\"token\"}", 180));
        replies.add(new Reply(200, "{\"id\":\"caller\",\"enabled\":true}", 250));
        long started = System.nanoTime();
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, assertThrows(CoreException.class,
            () -> guard(Duration.ofMillis(300)).requirePlatformAdmin()).code());
        assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 1500);
        assertTrue(requests.size() <= 2);
    }

    @Test void anonymousDoesNotContactKeycloak() {
        var actual = new KeycloakFreshAdminAuthorization(new WorkspaceAccessImpl(), new ObjectMapper(),
            base, "dx-lab", "dx-directory", "secret");
        assertEquals(ErrorCode.UNAUTHENTICATED, assertThrows(CoreException.class, actual::requirePlatformAdmin).code());
        assertTrue(requests.isEmpty());
    }

    @Test void encodesSubjectAsSinglePathSegment() {
        when(user.sub()).thenReturn("f:../other?admin=true");
        token();
        replies.add(new Reply(404, "{}"));
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CoreException.class,
            () -> guard(Duration.ofSeconds(5)).requirePlatformAdmin()).code());
        assertTrue(requests.get(1).contains("/users/f:..%2Fother%3Fadmin=true"));
    }

    @Test void missingConfigurationFailsClosedWithoutNetwork() {
        var actual = new KeycloakFreshAdminAuthorization(access, new ObjectMapper(), "", "dx-lab", "dx-directory", "");
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, assertThrows(CoreException.class, actual::requirePlatformAdmin).code());
        assertTrue(requests.isEmpty());
    }

    @Test void blankSubjectAndTraversalOnlySubjectsNeverReachDirectory() {
        for (String subject : new String[]{"", " ", ".", ".."}) {
            when(user.sub()).thenReturn(subject);
            assertEquals(ErrorCode.UNAUTHENTICATED, assertThrows(CoreException.class,
                () -> guard(Duration.ofSeconds(5)).requirePlatformAdmin()).code());
        }
        assertTrue(requests.isEmpty());
    }

    @Test void interruptedRequestFailsClosedAndPreservesInterrupt() {
        Thread.currentThread().interrupt();
        try {
            assertEquals(ErrorCode.SERVICE_UNAVAILABLE, assertThrows(CoreException.class,
                () -> guard(Duration.ofSeconds(5)).requirePlatformAdmin()).code());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
