package vn.haui.dx.hpdi.intelligence.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

import static org.junit.jupiter.api.Assertions.*;

class HttpAssistanceTransportTest {
    private static final String TOKEN = "synthetic-assistance-token-with-32-characters";
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicInteger calls = new AtomicInteger();
    private HttpServer server;
    private int status = 200;
    private String body = "{\"status\":\"SUCCESS\"}";

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/intelligence/v1/assistance", exchange -> {
            calls.incrementAndGet();
            assertEquals("Bearer " + TOKEN, exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HttpAssistanceTransport transport() {
        return new HttpAssistanceTransport(new AssistanceConfiguration.Settings(
            "http://127.0.0.1:" + server.getAddress().getPort(), TOKEN), json);
    }

    @Test
    void streamsBoundedEventsAndRejectsTruncatedOrInvalidFrames() throws Exception {
        var payload = new java.util.concurrent.atomic.AtomicReference<>("{\"type\":\"delta\",\"text\":\"First\"}\n{\"type\":\"done\",\"grounded\":true}\n");
        server.createContext("/internal/intelligence/v1/knowledge", exchange -> {
            assertEquals("Bearer " + TOKEN, exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(payload.get().getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            exchange.close();
        });
        var events = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        transport().streamKnowledge(json.createObjectNode(), events::add);
        assertEquals(java.util.List.of("delta", "done"), events.stream().map(event -> event.path("type").asText()).toList());
        for (String invalid : java.util.List.of("{\"type\":\"delta\"}", "{bad}\n", "{\"type\":\"done\",\"type\":\"error\"}\n", "x".repeat(65538) + "\n")) {
            payload.set(invalid);
            assertThrows(IntelligenceFailure.class, () -> transport().streamKnowledge(json.createObjectNode(), event -> {}));
        }
    }

    @Test
    void callsDedicatedPrivateRouteWithServiceCredential() {
        assertEquals("SUCCESS", transport().assist(json.createObjectNode()).path("status").asText());
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @CsvSource({"401,502", "403,502", "429,503", "502,502", "503,503", "504,504", "302,502", "400,502"})
    void typedErrorsDoNotBecomeSuccessOrEndUserAuthentication(int upstream, int expected) {
        status = upstream;
        var failure = assertThrows(IntelligenceFailure.class, () -> transport().assist(json.createObjectNode()));
        assertEquals(expected, failure.status());
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{} trailing", "{\"x\":1,\"x\":2}", "{", "NaN"})
    void malformedJsonFailsClosed(String payload) {
        body = payload;
        assertEquals(502, assertThrows(IntelligenceFailure.class,
            () -> transport().assist(json.createObjectNode())).status());
    }

    @Test
    void oversizedResponseAndUnapprovedEndpointAreRejected() {
        body = "x".repeat(128 * 1024 + 1);
        assertEquals(502, assertThrows(IntelligenceFailure.class,
            () -> transport().assist(json.createObjectNode())).status());

        var foreign = new HttpAssistanceTransport(new AssistanceConfiguration.Settings("http://example.org", TOKEN), json);
        assertThrows(IntelligenceFailure.class, () -> foreign.assist(json.createObjectNode()));
        assertEquals(1, calls.get());
    }
}
