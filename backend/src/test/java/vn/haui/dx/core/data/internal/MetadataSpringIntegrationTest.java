package vn.haui.dx.core.data.internal;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import vn.haui.dx.core.Errors;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.data.MetadataApi;
import vn.haui.dx.core.data.api.FileAccessPolicy;
import vn.haui.dx.core.data.api.FileService;
import vn.haui.dx.core.data.api.MetadataRecord;
import vn.haui.dx.core.data.api.MetadataService;
import vn.haui.dx.core.data.api.NewMetadata;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.Membership;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import vn.haui.dx.core.identity.api.WorkspaceRole;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.haui.dx.core.identity.api.WorkspaceRole.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class MetadataSpringIntegrationTest {
    @TempDir Path directory;
    private static DriverManagerDataSource source;
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager manager;
    private static final ObjectMapper json = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final User agent = new User("agent-spring", Set.of(new Membership("lab-a", AGENT)));
    private final User requester = new User("req-spring", Set.of(new Membership("lab-a", REQUESTER)));
    private final String fixedSha = "b".repeat(64);

    private record User(String sub, Set<Membership> memberships) implements CurrentUser {
        public String username() { return sub; }
        public boolean platformAdmin() { return false; }
        public boolean isMember(String workspace) { return memberships.stream().anyMatch(m -> m.workspace().equals(workspace)); }
        public boolean hasRole(String workspace, WorkspaceRole... roles) {
            return memberships.stream().anyMatch(m -> m.workspace().equals(workspace) && List.of(roles).contains(m.role()));
        }
    }

    @BeforeAll void setupDb() {
        source = new DriverManagerDataSource(System.getenv("FILE_TEST_JDBC_URL"), "workflow", System.getenv("FILE_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        manager = new DataSourceTransactionManager(source);
        Flyway.configure().dataSource(source).load().migrate();
    }

    @Configuration
    @EnableTransactionManagement
    static class SpringTxConfig {
        @Bean public javax.sql.DataSource dataSource() { return source; }
        @Bean public PlatformTransactionManager transactionManager(javax.sql.DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean public JdbcTemplate jdbcTemplate(javax.sql.DataSource ds) { return new JdbcTemplate(ds); }
        @Bean public MetadataRepository metadataRepository(JdbcTemplate jdbc) { return new MetadataRepository(jdbc); }
        @Bean public Validator validator() { return Validation.buildDefaultValidatorFactory().getValidator(); }
        @Bean public MetadataFileAccessPolicy metadataPolicy(MetadataRepository repo) { return new MetadataFileAccessPolicy(repo); }
        @Bean public JdbcMetadataService metadataService(MetadataRepository repo, WorkspaceAccess access,
                AuditService audit, Validator validator, org.springframework.beans.factory.ObjectProvider<FileService> files) {
            return new JdbcMetadataService(repo, access, audit, validator, files);
        }
    }

    private WorkspaceAccess access(User actor) {
        var access = mock(WorkspaceAccess.class);
        when(access.current()).thenReturn(actor);
        return access;
    }

    private AuditService audit(User actor) {
        var audit = mock(AuditService.class);
        doAnswer(call -> {
            jdbc.update("insert into audit_event(id,actor,action,resource_type,resource,workspace,request_id,details) values (?,?,?,?,?,?,?,cast(? as jsonb))",
                UUID.randomUUID(), actor.sub(), call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3),
                UUID.randomUUID().toString(), json.writeValueAsString(call.getArgument(4)));
            return null;
        }).when(audit).record(anyString(), anyString(), anyString(), anyString(), anyMap());
        return audit;
    }

    @Test void springProxyRollsBackMetadataAndLinkOnFailureViaAop() throws Exception {
        Path storageDir = directory.resolve(UUID.randomUUID().toString());
        var failingAudit = mock(AuditService.class);
        doThrow(new RuntimeException("Simulated audit disk failure during create")).when(failingAudit)
            .record(eq("metadata.create"), anyString(), anyString(), anyString(), anyMap());

        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(SpringTxConfig.class);
            context.registerBean(WorkspaceAccess.class, () -> access(agent));
            context.registerBean(AuditService.class, () -> failingAudit);
            context.registerBean(FileService.class, () -> new LocalFileService(storageDir, jdbc, access(agent),
                failingAudit, List.of(context.getBean(MetadataFileAccessPolicy.class)), manager));
            context.refresh();

            var metadataService = context.getBean(MetadataService.class);
            assertTrue(AopUtils.isAopProxy(metadataService), "MetadataService must be wrapped in a Spring transactional AOP proxy");

            var fileService = context.getBean(FileService.class);
            var stored = fileService.store("lab-a", "proxy-test.txt", "text/plain", new ByteArrayInputStream("hello proxy".getBytes(StandardCharsets.UTF_8)));

            String uniqueTitle = "TxRollbackProbe-" + UUID.randomUUID();
            // Invocation directly by caller without wrapping in caller-managed transaction
            assertThrows(RuntimeException.class, () -> metadataService.create("lab-a",
                new NewMetadata(uniqueTitle, "src:test", "v1", "CC0", null, stored.id())));

            // Assert rollback occurred in PostgreSQL
            assertEquals(0, jdbc.queryForObject("select count(*) from metadata where title=?", Integer.class, uniqueTitle));
            assertEquals(0, jdbc.queryForObject("select count(*) from file_link where file_id=?", Integer.class, stored.id()));
            // Stored file object itself was committed before metadata.create and is preserved
            assertEquals(1, jdbc.queryForObject("select count(*) from file_object where id=?", Integer.class, stored.id()));
        }
    }

    @Test void springProxyCommitsAllEntitiesTogetherOnSuccess() throws Exception {
        Path storageDir = directory.resolve(UUID.randomUUID().toString());
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(SpringTxConfig.class);
            context.registerBean(WorkspaceAccess.class, () -> access(agent));
            context.registerBean(AuditService.class, () -> audit(agent));
            context.registerBean(FileService.class, () -> new LocalFileService(storageDir, jdbc, access(agent),
                audit(agent), List.of(context.getBean(MetadataFileAccessPolicy.class)), manager));
            context.refresh();

            var metadataService = context.getBean(MetadataService.class);
            var fileService = context.getBean(FileService.class);
            var stored = fileService.store("lab-a", "commit-test.txt", "text/plain", new ByteArrayInputStream("commit content".getBytes(StandardCharsets.UTF_8)));

            String uniqueTitle = "TxCommitProbe-" + UUID.randomUUID();
            var record = metadataService.create("lab-a", new NewMetadata(uniqueTitle, "src:commit", "v1", "CC0", null, stored.id()));
            assertNotNull(record.id());

            assertEquals(1, jdbc.queryForObject("select count(*) from metadata where id=?", Integer.class, record.id()));
            assertEquals(1, jdbc.queryForObject("select count(*) from file_link where file_id=? and resource_id=?",
                Integer.class, stored.id(), record.id().toString()));
            assertTrue(jdbc.queryForObject("select count(*) from audit_event where resource=?", Integer.class, record.id().toString()) >= 1);
        }
    }

    @Test void transactionManagerAndDatasourceAreConsistent() {
        assertEquals(source, manager.getDataSource());
        assertEquals(source, jdbc.getDataSource());
    }

    @Test void mockMvcHttpResponseMatchesOpenApiMetadataSchemaExactly() throws Exception {
        var repo = new MetadataRepository(jdbc);
        var svc = new JdbcMetadataService(repo, access(agent), audit(agent),
            Validation.buildDefaultValidatorFactory().getValidator(), null);
        var controller = new MetadataApi(svc);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new Errors())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
            .build();

        // 1. POST creation with camelCase input
        String requestBody = """
            {
              "title": "OpenAPI Conformance Doc",
              "source": "team-authored:test",
              "version": "1.0",
              "usageRights": "CC0-1.0",
              "checksum": "%s"
            }
            """.formatted(fixedSha);

        String postResponse = mvc.perform(post("/api/core/v1/workspaces/lab-a/metadata")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode node = json.readTree(postResponse);
        // Verify exact fields according to OpenAPI core.yaml schema 'Metadata'
        // required: [id, workspace, title, source, version, usage_rights, checksum, file_id, owner, created_at]
        assertTrue(node.hasNonNull("id"), "id required");
        assertEquals("lab-a", node.get("workspace").asText());
        assertEquals("OpenAPI Conformance Doc", node.get("title").asText());
        assertEquals("team-authored:test", node.get("source").asText());
        assertEquals("1.0", node.get("version").asText());
        assertEquals("CC0-1.0", node.get("usage_rights").asText());
        assertEquals(fixedSha, node.get("checksum").asText());
        assertTrue(node.has("file_id"), "file_id must be present in JSON");
        assertTrue(node.get("file_id").isNull(), "file_id is null when not file-backed");
        assertFalse(node.has("fileId"), "fileId camelCase must NOT be serialized to output JSON");
        assertEquals("agent-spring", node.get("owner").asText());
        assertTrue(node.hasNonNull("created_at"), "created_at required");
        assertDoesNotThrow(() -> Instant.parse(node.get("created_at").asText()));

        UUID createdId = UUID.fromString(node.get("id").asText());

        // 2. GET by ID returns same OpenAPI schema
        String getResponse = mvc.perform(get("/api/core/v1/workspaces/lab-a/metadata/" + createdId))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode getNode = json.readTree(getResponse);
        assertEquals(node, getNode, "GET response must match POST response schema and values");

        // 3. GET search returns PageMeta with items array
        String searchResponse = mvc.perform(get("/api/core/v1/workspaces/lab-a/metadata")
                .param("q", "Conformance")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode searchNode = json.readTree(searchResponse);
        assertTrue(searchNode.has("items"), "PageMeta items required");
        assertEquals(0, searchNode.get("page").asInt());
        assertEquals(10, searchNode.get("size").asInt());
        assertTrue(searchNode.get("total").asLong() >= 1);
        JsonNode firstItem = searchNode.get("items").get(0);
        assertTrue(firstItem.has("file_id"));
        assertFalse(firstItem.has("fileId"));

        // 4. Request backward compatibility: accept snake_case in input
        String snakeBody = """
            {
              "title": "Snake Input Doc",
              "source": "team-authored:test",
              "version": "1.0",
              "usage_rights": "CC0-1.0",
              "checksum": "%s"
            }
            """.formatted(fixedSha);
        mvc.perform(post("/api/core/v1/workspaces/lab-a/metadata")
                .contentType(MediaType.APPLICATION_JSON)
                .content(snakeBody))
            .andExpect(status().isCreated());

        // 5. Exactly-one-of validation: both checksum and fileId supplied -> 400
        String bothBody = """
            {
              "title": "Both Doc",
              "source": "test",
              "version": "1",
              "usageRights": "CC0",
              "checksum": "%s",
              "fileId": "%s"
            }
            """.formatted(fixedSha, UUID.randomUUID());
        mvc.perform(post("/api/core/v1/workspaces/lab-a/metadata")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bothBody))
            .andExpect(status().isBadRequest());

        // 6. Exactly-one-of validation: neither supplied -> 400
        String neitherBody = """
            {
              "title": "Neither Doc",
              "source": "test",
              "version": "1",
              "usageRights": "CC0"
            }
            """;
        mvc.perform(post("/api/core/v1/workspaces/lab-a/metadata")
                .contentType(MediaType.APPLICATION_JSON)
                .content(neitherBody))
            .andExpect(status().isBadRequest());
    }
}
