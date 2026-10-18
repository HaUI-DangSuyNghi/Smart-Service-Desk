package vn.haui.dx.hpdi.intelligence.integration;

import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class HttpIntelligenceTransportTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private ExecutorService executor;
    private HttpIntelligenceTransport client;
    private int status = 200;
    private String body;
    private String mediaType = "application/json";
    private boolean delayHeaders;
    private boolean delayBody;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach void start() throws Exception {
        body = json.writeValueAsString(TriageResponses.success(TriageResponses.request()));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/internal/intelligence/v1/triage", this::respond);
        server.start();
        client = configured("http://127.0.0.1:" + server.getAddress().getPort(), "synthetic-service-token-for-tests-only");
    }

    private HttpIntelligenceTransport configured(String base, String token) {
        return new HttpIntelligenceTransport(new TriageConfiguration.Settings(base, token), json);
    }

    private void respond(HttpExchange exchange) throws java.io.IOException {
        calls.incrementAndGet();
        assertEquals("POST", exchange.getRequestMethod());
        assertEquals("Bearer synthetic-service-token-for-tests-only", exchange.getRequestHeaders().getFirst("Authorization"));
        assertEquals("application/json", exchange.getRequestHeaders().getFirst("Content-Type"));
        assertEquals(json.valueToTree(TriageResponses.request()), json.readTree(exchange.getRequestBody()));
        try (exchange) {
            if (delayHeaders) Thread.sleep(5000);
            exchange.getResponseHeaders().set("Content-Type", mediaType);
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/must-not-follow");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, delayBody ? 0 : bytes.length);
            if (delayBody) {
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                Thread.sleep(5000);
            }
            exchange.getResponseBody().write(bytes);
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }

    @AfterEach void stop() { server.stop(0); executor.shutdownNow(); }

    @Test void sendsOnlyServiceCredentialAndLogicalEnvelope() {
        assertEquals(TriageResponses.success(TriageResponses.request()), client.triage(TriageResponses.request()));
        assertEquals(1, calls.get());
    }

    @ParameterizedTest @CsvSource({"401,502", "403,502", "429,503", "500,503", "503,503", "404,502", "302,502", "307,502"})
    void mapsUpstreamStatusWithoutRetriesOrRedirects(int upstream, int expected) {
        status = upstream;
        var followed = new AtomicInteger();
        server.createContext("/must-not-follow", exchange -> { followed.incrementAndGet(); exchange.close(); });
        assertEquals(expected, assertThrows(IntelligenceFailure.class, () -> client.triage(TriageResponses.request())).status());
        assertEquals(1, calls.get());
        assertEquals(0, followed.get());
    }

    @ParameterizedTest @ValueSource(strings = {"not-json", "{} {}", "{\"status\":1,\"status\":2}"})
    void rejectsMalformedDuplicateAndTrailingJson(String value) {
        body = value;
        assertEquals(502, assertThrows(IntelligenceFailure.class, () -> client.triage(TriageResponses.request())).status());
    }

    @Test void capsResponseAndRejectsWrongMediaType() {
        body = "x".repeat(HttpIntelligenceTransport.MAX_RESPONSE + 1);
        assertEquals(502, assertThrows(IntelligenceFailure.class, () -> client.triage(TriageResponses.request())).status());
        body = "{}";
        mediaType = "text/html";
        assertEquals(502, assertThrows(IntelligenceFailure.class, () -> client.triage(TriageResponses.request())).status());
    }

    @Test void rejectsOversizedUtf8RequestBeforeIo() {
        var input = new TriageRequest("0.1.0", "id", "service-desk", new TriageRequest.Context("lab-a"),
            new TriageRequest.Input("title", "x".repeat(HttpIntelligenceTransport.MAX_REQUEST)));
        assertEquals(502, assertThrows(IntelligenceFailure.class, () -> client.triage(TriageResponses.envelope(input))).status());
        assertEquals(0, calls.get());
    }

    @Test void deadlineIncludesHeaders() {
        delayHeaders = true;
        assertEquals(504, assertThrows(IntelligenceFailure.class, () -> client.triage(TriageResponses.request())).status());
    }

    @Test void deadlineIncludesStalledBody() {
        delayBody = true;
        assertEquals(504, assertThrows(IntelligenceFailure.class, () -> client.triage(TriageResponses.request())).status());
    }

    @Test void connectionFailureAndDisabledAiDoNotPretendSuccess() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        var refused = configured("http://127.0.0.1:" + port, "synthetic-service-token-for-tests-only");
        assertEquals(503, assertThrows(IntelligenceFailure.class, () -> refused.triage(TriageResponses.request())).status());
        assertEquals(503, assertThrows(IntelligenceFailure.class, () -> configured("", "").triage(TriageResponses.request())).status());
    }

    @ParameterizedTest @ValueSource(strings = {"file:///secret", "http://user:pass@localhost", "http://localhost/path", "http://localhost?secret=x", "http://localhost#fragment", "http://localhost:99999"})
    void rejectsInvalidConfigurationWithoutNetwork(String base) {
        assertEquals(502, assertThrows(IntelligenceFailure.class, () -> configured(base, "synthetic-service-token-for-tests-only").triage(TriageResponses.request())).status());
    }

    @Test void rejectsMissingShortOrInvalidCredentialBeforeNetwork() {
        for (String token : new String[] {null, "", "short", "a".repeat(31), "a".repeat(31) + "=", " ".repeat(32), "a".repeat(32) + "\r\nAuthorization: evil"})
            assertEquals(502, assertThrows(IntelligenceFailure.class, () -> configured("http://127.0.0.1:" + server.getAddress().getPort(), token).triage(TriageResponses.request())).status());
        assertEquals(0, calls.get());
        assertFalse(new TriageConfiguration.Settings("http://host", "secret-value").toString().contains("secret-value"));
    }
}
