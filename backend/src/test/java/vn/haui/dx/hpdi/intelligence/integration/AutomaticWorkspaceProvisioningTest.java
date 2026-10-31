package vn.haui.dx.hpdi.intelligence.integration;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

class AutomaticWorkspaceProvisioningTest {
    @TempDir Path temporary;
    private HttpServer server;
    private Path token;
    private int calls;
    private int status = 200;
    private String reply = "{\"workspace\":\"lab-new\",\"ready\":true}";
    @BeforeEach void setup() throws Exception {
        token = temporary.resolve("token"); Files.writeString(token, "t".repeat(64));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/workspaces/lab-new", exchange -> {
            calls++;
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals("Bearer " + "t".repeat(64), exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals(0, exchange.getRequestBody().readAllBytes().length);
            byte[] body = reply.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    private AutomaticWorkspaceProvisioning provisioning() {
        var environment = new MockEnvironment(); environment.setActiveProfiles("local-dev");
        return new AutomaticWorkspaceProvisioning(true, "http://127.0.0.1:" + server.getAddress().getPort(), token.toString(), environment, new ObjectMapper());
    }
    @Test void bootstrapUsesAuthenticatedInternalEndpointWithoutAiInputs() {
        provisioning().prepare("lab-new"); assertEquals(1, calls);
    }
    @Test void deniedAndMissingWorkspaceNeverBecomeAuthorized() {
        status = 403;
        assertThrows(CapabilityException.class, () -> provisioning().prepare("lab-new"));
        status = 404;
        assertThrows(CapabilityException.class, () -> provisioning().prepare("lab-new"));
    }
    @Test void outageCanRetryButMalformedIdentityIsRejected() {
        status = 503;
        assertEquals(503, assertThrows(IntelligenceFailure.class, () -> provisioning().prepare("lab-new")).status());
        status = 200; reply = "{\"workspace\":\"other\",\"ready\":true}";
        assertEquals(502, assertThrows(IntelligenceFailure.class, () -> provisioning().prepare("lab-new")).status());
    }
    @Test void invalidWorkspaceNeverCallsBootstrap() {
        assertThrows(CapabilityException.class, () -> provisioning().prepare("../lab-new")); assertEquals(0, calls);
    }
    @Test void disabledModeAndProductionCannotUseProvisioning() {
        new AutomaticWorkspaceProvisioning(false, "", "", new MockEnvironment(), new ObjectMapper()).prepare("lab-new");
        assertEquals(0, calls);
        var environment = new MockEnvironment(); environment.setActiveProfiles("production", "local-dev");
        assertThrows(IllegalStateException.class, () -> new AutomaticWorkspaceProvisioning(true, "http://localhost:8005", token.toString(), environment, new ObjectMapper()));
        environment.setActiveProfiles("local-dev");
        assertThrows(IllegalStateException.class, () -> new AutomaticWorkspaceProvisioning(true, "https://example.org", token.toString(), environment, new ObjectMapper()));
    }
}
