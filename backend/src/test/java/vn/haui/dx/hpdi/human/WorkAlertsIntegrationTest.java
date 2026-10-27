package vn.haui.dx.hpdi.human;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.haui.dx.core.identity.api.*;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.human.internal.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class WorkAlertsIntegrationTest {
    private static final String ISSUER = "https://alerts.test/issuer";
    private static final com.nimbusds.jose.jwk.RSAKey KEY;
    private static final com.sun.net.httpserver.HttpServer JWKS;
    static {
        try {
            KEY = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("alerts-test").generate();
            JWKS = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/jwks", exchange -> {
                byte[] response = new com.nimbusds.jose.jwk.JWKSet(KEY.toPublicJWK()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            });
            JWKS.start();
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    @AfterAll static void stopKeys() { JWKS.stop(0); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkAlerts alerts;
    @Autowired AlertDelivery delivery;
    @Autowired PlatformTransactionManager manager;
    @Autowired WorkspaceDirectory directory;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    private String subject;
    private static final String BASE = "/api/hpdi/v1/workspaces/lab-a/alerts";
    @BeforeEach void members() {
        subject = "alert-" + UUID.randomUUID();
        when(directory.members("lab-a")).thenReturn(List.of(
            new DirectoryMember(subject, subject, null, Set.of(WorkspaceRole.COORDINATOR)),
            new DirectoryMember(subject + "-other", subject + "-other", null, Set.of(WorkspaceRole.COORDINATOR))));
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor as(String member, String workspace, String role) {
        return jwt().jwt(token -> token.subject(member).claim("groups", List.of("/workspaces/" + workspace + "/" + role)));
    }
    private WorkAlerts.Event event() {
        return new WorkAlerts.Event("lab-a", WorkAlerts.Type.CRITICAL_NEW, WorkAlerts.Severity.CRITICAL, "process-" + UUID.randomUUID(),
            "room-request", UUID.randomUUID().toString(), null, "review-1", "Coordinator approved CRITICAL", true, null);
    }
    private UUID publish(WorkAlerts.Event event) {
        new TransactionTemplate(manager).executeWithoutResult(status -> alerts.publish(event));
        return jdbc.queryForObject("select id from hpdi_alert_event where workspace=? and process_instance_id=?", UUID.class, event.workspace(), event.processInstanceId());
    }
    private UUID delivered() {
        UUID id = publish(event());
        delivery.deliver("lab-a", id, delivery.recipients("lab-a", true, null));
        return id;
    }
    @Test void recipientReadIsIndependentIdempotentAndWorkspaceIsolated() throws Exception {
        UUID id = delivered();
        var response = mvc.perform(get(BASE).with(as(subject, "lab-a", "coordinator"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.unread").value(1)).andExpect(jsonPath("$.items[0].processKey").value("room-request"))
            .andReturn().getResponse().getContentAsString();
        contract(response);
        for (int attempt = 0; attempt < 2; attempt++) mvc.perform(post(BASE + "/" + id + "/read").with(as(subject, "lab-a", "coordinator"))).andExpect(status().isNoContent());
        mvc.perform(get(BASE).with(as(subject, "lab-a", "coordinator"))).andExpect(jsonPath("$.unread").value(0));
        mvc.perform(get(BASE).with(as(subject + "-other", "lab-a", "coordinator"))).andExpect(jsonPath("$.unread").value(1));
        mvc.perform(post(BASE + "/" + id + "/read").with(as("stranger", "lab-a", "coordinator"))).andExpect(status().isForbidden());
        when(directory.members("lab-b")).thenReturn(List.of(new DirectoryMember(subject, subject, null, Set.of(WorkspaceRole.COORDINATOR))));
        mvc.perform(post(BASE.replace("lab-a", "lab-b") + "/" + id + "/read").with(as(subject, "lab-b", "coordinator"))).andExpect(status().isNotFound());
        mvc.perform(get(BASE).with(as(subject, "lab-b", "coordinator"))).andExpect(status().isForbidden());
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where action='human.alert.read' and resource=?", Integer.class, id.toString()));
    }
    @Test void paginationSinceAndUnreadFilterAreReadOnlyAndValidated() throws Exception {
        UUID first = delivered(); delivered();
        long before = jdbc.queryForObject("select count(*) from audit_event", Long.class);
        mvc.perform(get(BASE).param("size", "1").with(as(subject, "lab-a", "coordinator"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get(BASE).param("page", "1").param("size", "1").with(as(subject, "lab-a", "coordinator"))).andExpect(status().isOk());
        mvc.perform(get(BASE).param("since", "9999-01-01T00:00:00Z").with(as(subject, "lab-a", "coordinator"))).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.unread").value(2));
        mvc.perform(get(BASE).param("size", "101").with(as(subject, "lab-a", "coordinator"))).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("page", "-1").with(as(subject, "lab-a", "coordinator"))).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("since", "invalid").with(as(subject, "lab-a", "coordinator"))).andExpect(status().isBadRequest());
        assertEquals(before, jdbc.queryForObject("select count(*) from audit_event", Long.class));
        mvc.perform(post(BASE + "/" + first + "/read").with(as(subject, "lab-a", "coordinator"))).andExpect(status().isNoContent());
        mvc.perform(get(BASE).param("unreadOnly", "true").with(as(subject, "lab-a", "coordinator"))).andExpect(jsonPath("$.total").value(1));
    }
    @Test void currentMembershipRevocationHidesPreviouslyDeliveredReferences() throws Exception {
        UUID id = delivered();
        when(directory.members("lab-a")).thenReturn(List.of());
        mvc.perform(get(BASE).with(as(subject, "lab-a", "coordinator"))).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + id + "/read").with(as(subject, "lab-a", "coordinator"))).andExpect(status().isForbidden());
        mvc.perform(get(BASE).with(as(subject, "lab-a", "requester"))).andExpect(status().isForbidden());
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }
    @Test void losingDomainObserverRoleHidesReferencesEvenWithWorkspaceAgentPermission() throws Exception {
        UUID id = delivered();
        when(directory.members("lab-a")).thenReturn(List.of(new DirectoryMember(subject, subject, null, Set.of(WorkspaceRole.AGENT))));
        mvc.perform(get(BASE).with(as(subject, "lab-a", "agent"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.unread").value(0));
        mvc.perform(post(BASE + "/" + id + "/read").with(as(subject, "lab-a", "agent"))).andExpect(status().isNotFound());
    }
    @Test void concurrentPublishDeliveryAndReadDeduplicateAndSurviveServiceRecreation() throws Exception {
        var event = event();
        try (var pool = new ClosingPool()) {
            var first = pool.executor.submit(() -> publish(event));
            var second = pool.executor.submit(() -> publish(event));
            assertEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
        UUID id = publish(event);
        var recipients = delivery.recipients("lab-a", true, null);
        try (var pool = new ClosingPool()) {
            var first = pool.executor.submit(() -> delivery.deliver("lab-a", id, recipients));
            var second = pool.executor.submit(() -> delivery.deliver("lab-a", id, recipients));
            first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
        }
        assertEquals(2, jdbc.queryForObject("select count(*) from hpdi_alert_recipient where event_id=?", Integer.class, id));
        var restarted = new AlertDelivery(jdbc, directory, mock(ActivityJournal.class));
        restarted.deliver("lab-a", id, recipients);
        try (var pool = new ClosingPool()) {
            Callable<Integer> read = () -> mvc.perform(post(BASE + "/" + id + "/read").with(as(subject, "lab-a", "coordinator"))).andReturn().getResponse().getStatus();
            var first = pool.executor.submit(read); var second = pool.executor.submit(read);
            assertEquals(204, first.get(20, TimeUnit.SECONDS)); assertEquals(204, second.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where action='human.alert.read' and resource=?", Integer.class, id.toString()));
    }
    @Test void rollbackLeavesNoPhantomEventOrRecipientAndDeliveryCanRetry() {
        var event = event();
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager).executeWithoutResult(status -> { alerts.publish(event); throw new IllegalStateException("rollback"); }));
        assertEquals(0, jdbc.queryForObject("select count(*) from hpdi_alert_event where process_instance_id=?", Integer.class, event.processInstanceId()));
        UUID id = publish(event);
        delivery.deliver("lab-a", id, Set.of());
        assertNull(jdbc.queryForObject("select delivered_at from hpdi_alert_event where id=?", java.sql.Timestamp.class, id));
        delivery.deliver("lab-a", id, delivery.recipients("lab-a", true, null));
        assertNotNull(jdbc.queryForObject("select delivered_at from hpdi_alert_event where id=?", java.sql.Timestamp.class, id));
    }
    @Test void actualSignedJwtCanPollAndReadButInvalidAudienceCannot() throws Exception {
        UUID id = delivered();
        for (String audience : List.of("dx-core", "wrong-audience")) {
            var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer(ISSUER).subject(subject)
                .audience(List.of(audience)).issuedAt(java.time.Instant.now().minusSeconds(10)).expiresAt(java.time.Instant.now().plusSeconds(120))
                .claim("groups", List.of("/workspaces/lab-a/coordinator")).build();
            var encoder = new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(
                new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(KEY)));
            var header = org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).keyId("alerts-test").build();
            String token = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims)).getTokenValue();
            var response = mvc.perform(get(BASE).header("Authorization", "Bearer " + token));
            if ("dx-core".equals(audience)) {
                response.andExpect(status().isOk()).andExpect(jsonPath("$.unread").value(1));
                mvc.perform(post(BASE + "/" + id + "/read").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
            } else response.andExpect(status().isUnauthorized());
        }
    }
    @Test void auditFailureRollsBackDeliveryAndReadThenRetrySucceeds() throws Exception {
        UUID id = publish(event());
        jdbc.execute("create function reject_alert_delivery() returns trigger language plpgsql as $$ begin if new.action='human.alert.delivered' then raise exception 'reject delivery' using errcode='23514'; end if; return new; end $$");
        jdbc.execute("create trigger reject_alert_delivery before insert on audit_event for each row execute function reject_alert_delivery()");
        try {
            assertThrows(RuntimeException.class, () -> delivery.deliver("lab-a", id, Set.of(subject)));
            assertEquals(0, jdbc.queryForObject("select count(*) from hpdi_alert_recipient where event_id=?", Integer.class, id));
            assertNull(jdbc.queryForObject("select delivered_at from hpdi_alert_event where id=?", java.sql.Timestamp.class, id));
        } finally { jdbc.execute("drop trigger reject_alert_delivery on audit_event"); jdbc.execute("drop function reject_alert_delivery()"); }
        delivery.deliver("lab-a", id, Set.of(subject));
        jdbc.execute("create function reject_alert_read() returns trigger language plpgsql as $$ begin if new.action='human.alert.read' then raise exception 'reject read' using errcode='23514'; end if; return new; end $$");
        jdbc.execute("create trigger reject_alert_read before insert on audit_event for each row execute function reject_alert_read()");
        try {
            mvc.perform(post(BASE + "/" + id + "/read").with(as(subject, "lab-a", "coordinator"))).andExpect(status().isConflict());
            assertNull(jdbc.queryForObject("select read_at from hpdi_alert_recipient where event_id=? and subject=?", java.sql.Timestamp.class, id, subject));
        } finally { jdbc.execute("drop trigger reject_alert_read on audit_event"); jdbc.execute("drop function reject_alert_read()"); }
        mvc.perform(post(BASE + "/" + id + "/read").with(as(subject, "lab-a", "coordinator"))).andExpect(status().isNoContent());
    }
    @Test void failedWorkerDeliveryIsRetriedWithoutInventingRecipients() {
        UUID id = publish(event());
        var processing = mock(AlertProcessing.class);
        doThrow(new IllegalStateException("identity unavailable")).when(processing).run(eq("lab-a"), any());
        var worker = new AlertDeliveryWorker(jdbc, processing, delivery, true);
        worker.run();
        assertNull(jdbc.queryForObject("select delivered_at from hpdi_alert_event where id=?", java.sql.Timestamp.class, id));
        doAnswer(invocation -> { invocation.<Runnable>getArgument(1).run(); return null; }).when(processing).run(eq("lab-a"), any());
        jdbc.update("update hpdi_alert_event set retry_at=current_timestamp-interval '1 second' where id=?", id);
        worker.run();
        assertEquals(2, jdbc.queryForObject("select count(*) from hpdi_alert_recipient where event_id=?", Integer.class, id));
    }
    private static class ClosingPool implements AutoCloseable {
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        public void close() { executor.shutdownNow(); }
    }
    private void contract(String response) throws Exception {
        var specification = new org.yaml.snakeyaml.Yaml().load(java.nio.file.Files.readString(java.nio.file.Path.of("../contracts/hpdi/openapi.yaml")));
        var document = json.valueToTree(specification);
        var schema = json.createObjectNode().put("$ref", "#/components/schemas/Alert");
        schema.set("components", document.path("components"));
        normalize(schema);
        var validator = com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V7).getSchema(schema);
        for (var item : json.readTree(response).path("items")) assertTrue(validator.validate(item).isEmpty(), () -> validator.validate(item).toString());
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/r7-r8-alert-http-evidence.json"), json.writeValueAsString(List.of(
            Map.of("method", "GET", "path", BASE, "status", 200, "bodyJson", response))));
    }
    private void normalize(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isObject() && node.path("nullable").asBoolean() && node.path("type").isTextual()) {
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            object.set("type", json.createArrayNode().add(node.path("type").asText()).add("null"));
        }
        node.forEach(this::normalize);
    }
}
