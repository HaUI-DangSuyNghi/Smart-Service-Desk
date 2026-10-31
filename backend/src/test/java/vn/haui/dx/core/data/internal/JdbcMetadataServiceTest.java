package vn.haui.dx.core.data.internal;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.common.PageRequest;
import vn.haui.dx.core.data.MetadataApi;
import vn.haui.dx.core.data.api.FileService;
import vn.haui.dx.core.data.api.MetadataQuery;
import vn.haui.dx.core.data.api.NewMetadata;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.Membership;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import vn.haui.dx.core.identity.api.WorkspaceRole;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static vn.haui.dx.core.identity.api.WorkspaceRole.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class JdbcMetadataServiceTest {
    @TempDir Path directory;
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final User agent = new User("agent-1", Set.of(new Membership("lab-a", AGENT)));
    private final User coordinator = new User("coord-1", Set.of(new Membership("lab-a", COORDINATOR)));
    private final User requester = new User("req-1", Set.of(new Membership("lab-a", REQUESTER)));
    private final User foreign = new User("foreign-1", Set.of(new Membership("lab-b", COORDINATOR)));
    private final String fixedSha = "a".repeat(64);

    private record User(String sub, Set<Membership> memberships) implements CurrentUser {
        public String username() { return sub; }
        public boolean platformAdmin() { return false; }
        public boolean isMember(String workspace) { return memberships.stream().anyMatch(m -> m.workspace().equals(workspace)); }
        public boolean hasRole(String workspace, WorkspaceRole... roles) {
            return memberships.stream().anyMatch(m -> m.workspace().equals(workspace) && List.of(roles).contains(m.role()));
        }
    }

    @BeforeAll void migrate() {
        source = new DriverManagerDataSource(System.getenv("FILE_TEST_JDBC_URL"), "workflow", System.getenv("FILE_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        manager = new DataSourceTransactionManager(source);
        Flyway.configure().dataSource(source).load().migrate();
    }

    @AfterEach void cleanupContext() { SecurityContextHolder.clearContext(); }

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
                UUID.randomUUID().toString(), new ObjectMapper().writeValueAsString(call.getArgument(4)));
            return null;
        }).when(audit).record(anyString(), anyString(), anyString(), anyString(), anyMap());
        return audit;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<FileService> fileProvider(FileService fileService) {
        ObjectProvider<FileService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(fileService);
        return provider;
    }

    private static MetadataReadPolicy reads() {
        return new MetadataReadPolicy(
            mock(vn.haui.dx.core.identity.api.WorkspaceDirectory.class),
            mock(vn.haui.dx.core.identity.api.FreshAdminAuthorization.class));
    }

    private JdbcMetadataService service(User actor) { return service(actor, null, audit(actor)); }
    private JdbcMetadataService service(User actor, FileService fileService, AuditService auditService) {
        return new JdbcMetadataService(new MetadataRepository(jdbc), access(actor), auditService, validator, fileProvider(fileService), reads());
    }

    private LocalFileService fileService(User actor, MetadataRepository repository, Path storageRoot) {
        return new LocalFileService(storageRoot, jdbc, access(actor),
            audit(actor), List.of(new MetadataFileAccessPolicy(repository, reads())), manager);
    }

    private static void denied(ErrorCode expected, Executable executable) {
        var exception = assertThrows(CoreException.class, executable);
        assertEquals(expected, exception.code());
    }

    @Test void createAndReadWorkspaceShared() {
        var svcAgent = service(agent);
        var record = svcAgent.create("lab-a", new NewMetadata("Test Doc", "source:unit", "v1", "CC0-1.0", fixedSha, null));
        assertEquals("lab-a", record.workspace());
        assertEquals("agent-1", record.owner());
        assertEquals("Test Doc", record.title());
        assertNull(record.fileId());

        // Any member in lab-a can read; non-member is forbidden; other workspace is not found
        assertEquals(record.id(), service(requester).get("lab-a", record.id()).id());
        assertEquals(record.id(), service(coordinator).get("lab-a", record.id()).id());
        denied(ErrorCode.FORBIDDEN, () -> service(foreign).get("lab-a", record.id()));
        denied(ErrorCode.NOT_FOUND, () -> service(foreign).get("lab-b", record.id()));
    }

    @Test void requesterCannotCreateMetadata() {
        denied(ErrorCode.FORBIDDEN, () -> service(requester).create("lab-a",
            new NewMetadata("Denied", "source:unit", "v1", "CC0-1.0", fixedSha, null)));
        denied(ErrorCode.FORBIDDEN, () -> service(foreign).create("lab-a",
            new NewMetadata("Denied", "source:unit", "v1", "CC0-1.0", fixedSha, null)));
    }

    @Test void validationRejectsMissingInvalidAndControlCharacters() {
        var svc = service(agent);
        denied(ErrorCode.VALIDATION_FAILED, () -> svc.create("lab-a", null));
        denied(ErrorCode.VALIDATION_FAILED, () -> svc.create("lab-a", new NewMetadata("", "src", "v1", "CC0", fixedSha, null)));
        denied(ErrorCode.VALIDATION_FAILED, () -> svc.create("lab-a", new NewMetadata("t".repeat(201), "src", "v1", "CC0", fixedSha, null)));
        denied(ErrorCode.VALIDATION_FAILED, () -> svc.create("lab-a", new NewMetadata("t", "s".repeat(1001), "v1", "CC0", fixedSha, null)));
        denied(ErrorCode.VALIDATION_FAILED, () -> svc.create("lab-a", new NewMetadata("title\u0000bad", "src", "v1", "CC0", fixedSha, null)));
        denied(ErrorCode.VALIDATION_FAILED, () -> svc.create("lab-a", new NewMetadata("t", "src", "v1", "CC0", "bad-sha", null)));
        // must have exactly one of checksum or fileId
        denied(ErrorCode.VALIDATION_FAILED, () -> svc.create("lab-a", new NewMetadata("t", "src", "v1", "CC0", null, null)));
        denied(ErrorCode.VALIDATION_FAILED, () -> svc.create("lab-a", new NewMetadata("t", "src", "v1", "CC0", fixedSha, UUID.randomUUID())));
    }

    @Test void searchFilterAndPagination() {
        var svc = service(coordinator);
        String prefix = UUID.randomUUID().toString().substring(0, 8);
        String src1 = "src:" + prefix + "-1";
        String src2 = "src:" + prefix + "-2";
        svc.create("lab-a", new NewMetadata(prefix + " Alpha report", src1, "v1", "CC0", fixedSha, null));
        svc.create("lab-a", new NewMetadata(prefix + " Beta analysis", src2, "v1", "CC0", fixedSha, null));
        svc.create("lab-a", new NewMetadata(prefix + " Gamma review", src1, "v1", "CC0", fixedSha, null));

        var page1 = svc.search("lab-a", new MetadataQuery(prefix, null, "title,asc"), new PageRequest(0, 2));
        assertEquals(3, page1.total());
        assertEquals(2, page1.items().size());
        assertEquals(prefix + " Alpha report", page1.items().get(0).title());
        assertEquals(prefix + " Beta analysis", page1.items().get(1).title());

        var page2 = svc.search("lab-a", new MetadataQuery(prefix, null, "title,asc"), new PageRequest(1, 2));
        assertEquals(1, page2.items().size());
        assertEquals(prefix + " Gamma review", page2.items().get(0).title());

        // Filter by source
        var bySource = svc.search("lab-a", new MetadataQuery(prefix, src1, "createdAt,desc"), new PageRequest(0, 10));
        assertEquals(2, bySource.total());
        assertTrue(bySource.items().stream().allMatch(item -> item.source().equals(src1)));

        // Cross-workspace query sees zero items
        var foreignSearch = service(foreign).search("lab-b", new MetadataQuery(prefix, null), new PageRequest(0, 10));
        assertEquals(0, foreignSearch.total());
    }

    @Test void fileBackedMetadataComputesChecksumAndLinksAutomatically() throws Exception {
        Path storageRoot = directory.resolve(UUID.randomUUID().toString());
        var repo = new MetadataRepository(jdbc);
        var files = fileService(agent, repo, storageRoot);
        byte[] bytes = "Hello provenance file".getBytes(StandardCharsets.UTF_8);
        var stored = files.store("lab-a", "doc.txt", "text/plain", new ByteArrayInputStream(bytes));

        var metadataSvc = new JdbcMetadataService(repo, access(agent), audit(agent), validator, fileProvider(files), reads());
        var record = metadataSvc.create("lab-a", new NewMetadata("File Doc", "src:file", "v1", "CC0", null, stored.id()));
        assertEquals(stored.checksum(), record.checksum());
        assertEquals(stored.id(), record.fileId());

        // Since file is now linked to metadata, member can read file via MetadataFileAccessPolicy
        var requesterFiles = fileService(requester, repo, storageRoot);
        assertNotNull(requesterFiles.describe("lab-a", stored.id()));
        try (var stream = requesterFiles.open("lab-a", stored.id())) {
            assertArrayEquals(bytes, stream.readAllBytes());
        }
    }

    @Test void metadataCannotAttachOtherUsersFile() throws Exception {
        Path storageRoot = directory.resolve(UUID.randomUUID().toString());
        var repo = new MetadataRepository(jdbc);
        var files = fileService(agent, repo, storageRoot);
        var stored = files.store("lab-a", "agent.txt", "text/plain", new ByteArrayInputStream("agent".getBytes(StandardCharsets.UTF_8)));

        var coordSvc = new JdbcMetadataService(repo, access(coordinator), audit(coordinator), validator, fileProvider(files), reads());
        denied(ErrorCode.NOT_FOUND, () -> coordSvc.create("lab-a",
            new NewMetadata("Stolen", "src:stolen", "v1", "CC0", null, stored.id())));
    }

    @Test void crossWorkspaceFileCannotBeAttached() {
        UUID foreignFileId = UUID.randomUUID();
        jdbc.update("insert into file_object(id,workspace,owner,storage_key,media_type,byte_size,checksum,original_name) "
            + "values (?,'lab-b','foreign',?,'text/plain',1,?,'file.txt')",
            foreignFileId, UUID.randomUUID(), fixedSha);

        var repo = new MetadataRepository(jdbc);
        var files = mock(FileService.class);
        when(files.describe("lab-a", foreignFileId)).thenThrow(CoreException.notFound("File not found"));
        var svc = new JdbcMetadataService(repo, access(agent), audit(agent), validator, fileProvider(files), reads());
        denied(ErrorCode.NOT_FOUND, () -> svc.create("lab-a",
            new NewMetadata("Cross", "src:cross", "v1", "CC0", null, foreignFileId)));
    }

    @Test void auditFailureRollsBackMetadataAndLink() throws Exception {
        Path storageRoot = directory.resolve(UUID.randomUUID().toString());
        var repo = new MetadataRepository(jdbc);
        var files = fileService(agent, repo, storageRoot);
        var stored = files.store("lab-a", "doc.txt", "text/plain", new ByteArrayInputStream("text".getBytes(StandardCharsets.UTF_8)));

        var brokenAudit = mock(AuditService.class);
        doThrow(new RuntimeException("Audit disk full")).when(brokenAudit).record(anyString(), anyString(), anyString(), anyString(), anyMap());
        var tx = new TransactionTemplate(manager);
        var svc = new JdbcMetadataService(repo, access(agent), brokenAudit, validator, fileProvider(files), reads());

        assertThrows(RuntimeException.class, () -> tx.execute(status ->
            svc.create("lab-a", new NewMetadata("Rollback", "src:rollback", "v1", "CC0", null, stored.id()))));
        assertEquals(0, jdbc.queryForObject("select count(*) from metadata where title='Rollback'", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from file_link where file_id=?", Integer.class, stored.id()));
    }

    @Test void concurrentInsertsDoNotCorruptSearchOrderOrPaging() throws Exception {
        var svc = service(coordinator);
        String tag = "concurrent-" + UUID.randomUUID();
        var latch = new java.util.concurrent.CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(4);
        try {
            for (int i = 0; i < 8; i++) {
                final int index = i;
                pool.submit(() -> {
                    try {
                        latch.await();
                        svc.create("lab-a", new NewMetadata(tag + " Item " + index, "src:test", "v" + index, "CC0", fixedSha, null));
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
            }
            latch.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            var page = svc.search("lab-a", new MetadataQuery(tag, null, "createdAt,desc"), new PageRequest(0, 20));
            assertEquals(8, page.total());
            assertEquals(8, page.items().size());
        } finally { pool.shutdownNow(); }
    }

    @Test void controllerSerializesV1CompatibleFields() {
        var svc = service(agent);
        var controller = new MetadataApi(svc);
        var view = controller.create("lab-a", new NewMetadata("API Title", "src:api", "v1", "CC0", fixedSha, null));
        assertEquals("API Title", view.title());
        assertEquals("CC0", view.usageRights());
        assertEquals(view.id(), controller.get("lab-a", view.id()).id());
        var page = controller.search("lab-a", "API", null, "createdAt,desc", 0, 10);
        assertTrue(page.items().stream().anyMatch(item -> item.id().equals(view.id())));
    }
}
