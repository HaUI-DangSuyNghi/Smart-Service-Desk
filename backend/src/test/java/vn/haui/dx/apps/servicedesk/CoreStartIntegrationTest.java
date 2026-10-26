package vn.haui.dx.apps.servicedesk;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.haui.dx.core.workflow.internal.WorkflowStateFixture;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(WorkflowStateFixture.class)
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class CoreStartIntegrationTest {
    private static final String ISSUER = "https://start.test/issuer";
    private static final RSAKey KEY;
    private static final HttpServer JWKS;
    private static final Path STORAGE = Path.of(System.getProperty("java.io.tmpdir"), "start-test-" + UUID.randomUUID());
    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("start-test").generate();
            JWKS = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/jwks", exchange -> {
                byte[] response = new JWKSet(KEY.toPublicJWK()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            });
            JWKS.start();
        } catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    @AfterAll static void stopKeys() { JWKS.stop(0); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("dx.storage.root", STORAGE::toString);
        properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkflowStateFixture engineState;

    private String token() {
        var claims = JwtClaimsSet.builder().issuer(ISSUER).subject("signed-start")
            .audience(List.of("dx-core")).issuedAt(Instant.now().minusSeconds(1)).expiresAt(Instant.now().plusSeconds(300))
            .claim("groups", List.of("/workspaces/lab-a/requester")).build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256)
            .keyId("start-test").build(), claims)).getTokenValue();
    }
    private long[] counts() {
        return java.util.stream.LongStream.concat(java.util.stream.LongStream.of(
            jdbc.queryForObject("select count(*) from servicedesk_ticket", Long.class)),
            java.util.Arrays.stream(engineState.counts())).toArray();
    }
    @Test void directApplicationStartCannotCreateOrphan() throws Exception {
        long[] before = counts();
        String businessKey = UUID.randomUUID().toString();
        var response = mvc.perform(post("/api/core/v1/workspaces/lab-a/processes/service-desk/instances")
            .header("Authorization", "Bearer " + token()).contentType("application/json")
            .content("{\"businessKey\":\"" + businessKey + "\",\"variables\":{}}"))
            .andReturn().getResponse();
        System.out.println("START-GAP status=" + response.getStatus() + " before=" + java.util.Arrays.toString(before)
            + " after=" + java.util.Arrays.toString(counts()) + " businessKey=" + businessKey);
        assertAll(() -> assertEquals(403, response.getStatus()), () -> assertArrayEquals(before, counts()));
    }
    @Test void signedUserCanStillStartSelfServiceProcess() throws Exception {
        long[] before = counts();
        mvc.perform(post("/api/core/v1/workspaces/lab-a/processes/document-review/instances")
            .header("Authorization", "Bearer " + token()).contentType("application/json")
            .content("{\"businessKey\":\"" + UUID.randomUUID() + "\",\"variables\":{\"title\":\"Review\"}}"))
            .andExpect(status().isCreated());
        long[] after = counts();
        assertArrayEquals(new long[]{before[0], before[1] + 1, before[2] + 1, before[3] + 1, before[4] + 1}, after);
    }
    @Test void signedUserCannotStartInForeignWorkspace() throws Exception {
        long[] before = counts();
        mvc.perform(post("/api/core/v1/workspaces/lab-b/processes/document-review/instances")
            .header("Authorization", "Bearer " + token()).contentType("application/json")
            .content("{\"businessKey\":\"" + UUID.randomUUID() + "\",\"variables\":{}}"))
            .andExpect(status().isForbidden());
        assertArrayEquals(before, counts());
    }
    @Test void signedUserCreatesTicketThroughApplicationWithLinkedWorkflowAndAudit() throws Exception {
        long[] before = counts();
        UUID requestId = UUID.randomUUID();
        mvc.perform(post("/api/apps/servicedesk/v1/workspaces/lab-a/tickets")
            .header("Authorization", "Bearer " + token()).contentType("application/json")
            .content("{\"clientRequestId\":\"" + requestId + "\",\"title\":\"Support\",\"description\":\"Synthetic request\"}"))
            .andExpect(status().isCreated());
        assertArrayEquals(new long[]{before[0] + 1, before[1] + 1, before[2] + 1, before[3] + 1, before[4] + 2}, counts());
        String processId = jdbc.queryForObject("select process_instance_id from servicedesk_ticket where client_request_id=?",
            String.class, requestId);
        assertEquals(1L, jdbc.queryForObject("select count(*) from process_scope where id=? and owner='signed-start'",
            Long.class, processId));
    }
}
