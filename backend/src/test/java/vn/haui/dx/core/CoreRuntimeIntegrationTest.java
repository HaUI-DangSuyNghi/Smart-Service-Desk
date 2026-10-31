package vn.haui.dx.core;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="FILE_TEST_JDBC_URL", matches=".+/file_storage")
class CoreRuntimeIntegrationTest {
    private static final Path STORAGE = Path.of(System.getProperty("java.io.tmpdir"), "core-runtime-" + UUID.randomUUID());
    private static final String ISSUER = "https://runtime.test/issuer";
    private static final com.nimbusds.jose.jwk.RSAKey KEY;
    private static final com.sun.net.httpserver.HttpServer JWKS;
    static {
        try {
            KEY = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("runtime-test").generate();
            JWKS = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/jwks", exchange -> {
                byte[] response = new com.nimbusds.jose.jwk.JWKSet(KEY.toPublicJWK()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            });
            JWKS.start();
        } catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    @org.junit.jupiter.api.AfterAll static void stopKeys() { JWKS.stop(0); }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("dx.storage.root", STORAGE::toString);
        properties.add("dx.storage.allow-unscanned-upload", () -> "true");
        properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test void compiledModulesMatchTheRequestedIsolationMode() throws Exception {
        if (Boolean.getBoolean("dx.proof.core-only")) {
            for (String type : List.of("vn.haui.dx.apps.servicedesk.Ticket", "vn.haui.dx.hpdi.process.api.ProcessCapability",
                    "vn.haui.dx.assembly.DxOsApplication"))
                assertThrows(ClassNotFoundException.class, () -> Class.forName(type));
            assertNull(jdbc.queryForObject("select to_regclass('servicedesk_ticket')::text", String.class));
            assertNull(getClass().getResource("/apps/servicedesk/process/service-desk.bpmn20.xml"));
        } else {
            assertNotNull(Class.forName("vn.haui.dx.assembly.DxOsApplication"));
            assertNotNull(Class.forName("vn.haui.dx.hpdi.process.api.ProcessCapability"));
        }
    }


    private String signedToken(String issuer, String audience, java.time.Instant expires,
                               com.nimbusds.jose.jwk.RSAKey key) {
        return signedToken(issuer, audience, expires, key, List.of("/workspaces/lab-a/requester"));
    }

    private String signedToken(String issuer, String audience, java.time.Instant expires,
                               com.nimbusds.jose.jwk.RSAKey key, List<String> groups) {
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer(issuer).subject("signed-runtime")
            .audience(List.of(audience)).issuedAt(java.time.Instant.now().minusSeconds(300)).expiresAt(expires)
            .claim("groups", groups).build();
        var encoder = new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(
            new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(key)));
        var header = org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
            .keyId("runtime-test").build();
        return encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Autowired vn.haui.dx.core.identity.api.WorkspaceDirectory directory;

    @Test void realSignedUserCannotRetainDocumentAccessAfterPolicyRefresh() throws Exception {
        var original = directory.rolePermissions("lab-a");
        String token = signedToken(ISSUER, "dx-core", java.time.Instant.now().plusSeconds(300), KEY);
        try {
            org.mockito.Mockito.doReturn(java.util.Map.of("requester", List.of(
                new vn.haui.dx.core.identity.api.DirectoryPermission("document:view", null))))
                .when(directory).rolePermissions("lab-a");
            mvc.perform(get("/api/core/v1/workspaces/lab-a/metadata").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
            org.mockito.Mockito.doReturn(java.util.Map.of()).when(directory).rolePermissions("lab-a");
            for (String path : List.of("metadata", "metadata/00000000-0000-0000-0000-000000000001",
                    "files/00000000-0000-0000-0000-000000000001", "files/00000000-0000-0000-0000-000000000001/content")) {
                mvc.perform(get("/api/core/v1/workspaces/lab-a/" + path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
            }
        } finally { org.mockito.Mockito.doReturn(original).when(directory).rolePermissions("lab-a"); }
    }

    @Test void signedServiceIdentityWithoutWorkspaceAndPrivateIntelligenceTokenCannotReadCore() throws Exception {
        String token = signedToken(ISSUER, "dx-core", java.time.Instant.now().plusSeconds(300), KEY, List.of());
        mvc.perform(get("/api/core/v1/workspaces/lab-a/metadata").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/core/v1/workspaces/lab-a/metadata")
            .header("Authorization", "Bearer " + "a".repeat(64))).andExpect(status().isUnauthorized());
    }

    @Test void realJwtDecoderRejectsWrongIssuerAudienceExpiryAndSignature() throws Exception {
        var future = java.time.Instant.now().plusSeconds(300);
        String valid = signedToken(ISSUER, "dx-core", future, KEY);
        mvc.perform(get("/api/core/v1/me").header("Authorization", "Bearer " + valid))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sub").value("signed-runtime"));
        var otherKey = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("runtime-test").generate();
        for (String invalid : List.of(signedToken("https://wrong.test", "dx-core", future, KEY),
                signedToken(ISSUER, "wrong-audience", future, KEY),
                signedToken(ISSUER, "dx-core", java.time.Instant.now().minusSeconds(120), KEY),
                signedToken(ISSUER, "dx-core", future, otherKey))) {
            mvc.perform(get("/api/core/v1/me").header("Authorization", "Bearer " + invalid))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
        mvc.perform(get("/api/core/v1/me")).andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor actor(String subject, String workspace, String role) {
        return jwt().jwt(token -> token.subject(subject)
            .claim("groups", List.of("/workspaces/" + workspace + "/" + role)));
    }
    private RequestPostProcessor requester() { return actor("runtime-requester", "lab-a", "requester"); }
    private RequestPostProcessor reviewer() { return actor("runtime-reviewer", "lab-a", "coordinator"); }
    private RequestPostProcessor agent() { return actor("runtime-agent", "lab-a", "agent"); }
    private JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
    private JsonNode start(String key) throws Exception {
        return body(mvc.perform(post("/api/core/v1/workspaces/lab-a/processes/" + key + "/instances")
            .with(requester()).contentType("application/json")
            .content(json.writeValueAsString(java.util.Map.of("businessKey", UUID.randomUUID().toString(), "title", "Runtime"))))
            .andExpect(status().isCreated()));
    }

    @Test void securityAndIdentityAreRequestScoped() throws Exception {
        var files = org.mockito.Mockito.mock(vn.haui.dx.core.data.api.FileService.class);
        var uploads = new vn.haui.dx.core.data.FileApi(files, false);
        var denied = assertThrows(vn.haui.dx.core.common.CoreException.class,
            () -> uploads.upload("lab-a", new MockMultipartFile("file", "gate.txt", "text/plain", new byte[0])));
        assertEquals(vn.haui.dx.core.common.ErrorCode.FORBIDDEN, denied.code());
        org.mockito.Mockito.verifyNoInteractions(files);
        mvc.perform(get("/api/core/v1/me")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/core/v1/me").with(requester()))
            .andExpect(jsonPath("$.sub").value("runtime-requester"));
        mvc.perform(get("/api/core/v1/me").with(agent()))
            .andExpect(jsonPath("$.sub").value("runtime-agent"));
        mvc.perform(get("/api/core/v1/workspaces/lab-a/tasks").with(actor("foreign", "lab-b", "coordinator")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/core/v1/workspaces/lab-a/tasks?scope=invalid").with(agent()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/core/v1/me")).andExpect(status().isUnauthorized());
    }

    @Autowired org.flowable.engine.ProcessEngine engine;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;

    @Test void workflowAuditFailureRollsBackEngineAndScope() throws Exception {
        var config = (org.flowable.spring.SpringProcessEngineConfiguration) engine.getProcessEngineConfiguration();
        assertSame(manager, config.getTransactionManager());
        assertSame(jdbc.getDataSource(), ((org.springframework.jdbc.datasource.DataSourceTransactionManager) manager).getDataSource());
        long scopes = jdbc.queryForObject("select count(*) from process_scope", Long.class);
        long processes = engine.getHistoryService().createHistoricProcessInstanceQuery().count();
        long audits = jdbc.queryForObject("select count(*) from audit_event", Long.class);
        jdbc.execute("create function runtime_fail_audit() returns trigger language plpgsql as $$ begin "
            + "if NEW.action='process.start' then raise exception 'injected runtime audit failure' using errcode='23514'; end if; return NEW; end $$");
        try {
            jdbc.execute("create trigger runtime_fail_audit before insert on audit_event for each row execute function runtime_fail_audit()");
            mvc.perform(post("/api/core/v1/workspaces/lab-a/processes/document-review/instances").with(requester())
                .contentType("application/json").content("{\"businessKey\":\"rollback-runtime\",\"title\":\"Rollback\"}"))
                .andExpect(status().isConflict());
            assertEquals(scopes, jdbc.queryForObject("select count(*) from process_scope", Long.class));
            assertEquals(processes, engine.getHistoryService().createHistoricProcessInstanceQuery().count());
            assertEquals(audits, jdbc.queryForObject("select count(*) from audit_event", Long.class));
        } finally {
            jdbc.execute("drop trigger if exists runtime_fail_audit on audit_event");
            jdbc.execute("drop function runtime_fail_audit()");
        }
    }

    @Test void fileMetadataAndAuditUseRealRuntime() throws Exception {
        String base = "/api/core/v1/workspaces/lab-a";
        byte[] content = "Synthetic runtime document".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var file = body(mvc.perform(multipart(base + "/files").file(new MockMultipartFile("file", "runtime.txt", "text/plain", content))
            .with(agent())).andExpect(status().isCreated()));
        String fileId = file.get("id").asText();
        mvc.perform(get(base + "/files/" + fileId).with(requester())).andExpect(status().isForbidden());
        mvc.perform(get(base + "/files/" + fileId + "/content").with(requester())).andExpect(status().isForbidden());
        mvc.perform(get(base + "/files/" + fileId + "/content").with(agent()))
            .andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
            .andExpect(content().bytes(content));
        var metadata = body(mvc.perform(post(base + "/metadata").with(agent()).contentType("application/json")
            .content(json.writeValueAsString(java.util.Map.of("title", "Runtime file", "source", "synthetic", "version", "1",
                "usageRights", "CC0", "fileId", fileId))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.fileId").value(fileId))
            .andExpect(jsonPath("$.file_id").doesNotExist()));
        assertEquals(file.get("checksum"), metadata.get("checksum"));
        mvc.perform(get(base + "/files/" + fileId + "/content").with(requester())).andExpect(status().isForbidden());
        mvc.perform(get(base + "/files/" + fileId + "/content").with(agent())).andExpect(status().isOk());
        mvc.perform(get("/api/core/v1/workspaces/lab-b/files/" + fileId).with(actor("foreign", "lab-b", "agent")))
            .andExpect(status().isNotFound());
        mvc.perform(get(base + "/audit?action=metadata.create").with(reviewer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
        mvc.perform(get(base + "/audit").with(agent())).andExpect(status().isForbidden());
        mvc.perform(multipart(base + "/files").file(new MockMultipartFile("file", "bad.exe", "application/octet-stream", content))
            .with(agent())).andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart(base + "/files").file(new MockMultipartFile("file", "big.txt", "text/plain", new byte[5242881]))
            .with(agent())).andExpect(status().isPayloadTooLarge());
    }

    @Test void independentWorkflowAndParentBinding() throws Exception {
        mvc.perform(get("/api/core/v1/process-definitions").with(requester()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.processKey == 'document-review')]").isNotEmpty())
            .andExpect(jsonPath("$[?(@.processKey == 'equipment-approval')]").isNotEmpty());
        for (String key : List.of("document-review", "equipment-approval")) {
            var process = start(key);
            String id = process.get("processInstanceId").asText();
            assertEquals(id, process.get("id").asText());
            String base = "/api/core/v1/workspaces/lab-a/instances/" + id;
            var tasks = body(mvc.perform(get(base + "/tasks").with(requester())).andExpect(status().isOk()));
            String taskId = tasks.get(0).get("id").asText();
            String taskKey = tasks.get(0).get("key").asText();
            mvc.perform(post(base + "/tasks/" + taskId + "/claim").with(requester())).andExpect(status().isForbidden());
            var other = start("document-review");
            mvc.perform(post("/api/core/v1/workspaces/lab-a/instances/" + other.get("id").asText() + "/tasks/" + taskId + "/claim")
                .with(reviewer())).andExpect(status().isNotFound());
            mvc.perform(post(base + "/tasks/" + taskId + "/claim").with(reviewer())).andExpect(status().isOk());
            mvc.perform(post(base + "/tasks/" + taskId + "/complete").with(agent()).contentType("application/json")
                .content("{\"expectedTaskKey\":\"" + taskKey + "\",\"outcome\":\"approved\"}"))
                .andExpect(status().isForbidden());
            mvc.perform(post(base + "/tasks/" + taskId + "/complete").with(reviewer()).contentType("application/json")
                .content("{\"expectedTaskKey\":\"" + taskKey + "\",\"outcome\":\"approved\",\"comment\":\"Reviewed\"}"))
                .andExpect(status().isNoContent());
            if (key.equals("equipment-approval")) {
                var inbox = body(mvc.perform(get("/api/core/v1/workspaces/lab-a/tasks?scope=candidate").with(agent()))
                    .andExpect(status().isOk()));
                String handover = java.util.stream.StreamSupport.stream(inbox.get("items").spliterator(), false)
                    .filter(task -> task.get("processInstanceId").asText().equals(id)).findFirst().orElseThrow().get("id").asText();
                mvc.perform(post(base + "/tasks/" + handover + "/claim").with(agent())).andExpect(status().isOk());
                mvc.perform(post(base + "/tasks/" + handover + "/complete").with(agent()).contentType("application/json")
                    .content("{\"expectedTaskKey\":\"handover\",\"outcome\":\"delivered\"}"))
                    .andExpect(status().isNoContent());
            }
            mvc.perform(get(base + "/history").with(requester())).andExpect(status().isOk())
                .andExpect(jsonPath("$.endedAt").isNotEmpty()).andExpect(jsonPath("$.steps[0].assignee").value("runtime-reviewer"))
                .andExpect(jsonPath("$.steps[0].comment").value("Reviewed"));
        }
    }
}
