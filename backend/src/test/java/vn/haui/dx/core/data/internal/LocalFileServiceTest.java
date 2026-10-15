package vn.haui.dx.core.data.internal;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.data.api.FileAccessPolicy;
import vn.haui.dx.core.data.api.FileService;
import vn.haui.dx.core.data.api.StoredFile;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.Membership;
import vn.haui.dx.core.identity.api.WorkspaceRole;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static vn.haui.dx.core.identity.api.WorkspaceRole.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class LocalFileServiceTest {
    @TempDir Path directory;
    private Path root;
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private final User owner = new User("owner", Set.of(new Membership("lab-a", REQUESTER)));
    private final User reviewer = new User("reviewer", Set.of(new Membership("lab-a", COORDINATOR)));
    private final User peer = new User("peer", Set.of(new Membership("lab-a", AGENT)));
    private final User foreign = new User("foreign", Set.of(new Membership("lab-b", COORDINATOR)));
    private final FileAccessPolicy policy = new FileAccessPolicy() {
        public String resourceType() { return "test.resource"; }
        public boolean canRead(CurrentUser user, String workspace, String id) {
            return workspace.equals("lab-a") && Set.of("R-1", "R-2").contains(id)
                && (Set.of("owner", "reviewer").contains(user.sub()) || (id.equals("R-2") && user.sub().equals("r2-reader")));
        }
        public boolean canLink(CurrentUser user, String workspace, String id) {
            return workspace.equals("lab-a") && user.sub().equals("owner") && Set.of("R-1", "R-2").contains(id);
        }
    };

    private record User(String sub, Set<Membership> memberships) implements CurrentUser {
        public String username() { return sub; }
        public boolean platformAdmin() { return false; }
        public boolean isMember(String workspace) { return memberships.stream().anyMatch(member -> member.workspace().equals(workspace)); }
        public boolean hasRole(String workspace, WorkspaceRole... roles) {
            return memberships.stream().anyMatch(member -> member.workspace().equals(workspace) && List.of(roles).contains(member.role()));
        }
    }

    @BeforeAll void migrate() {
        source = new DriverManagerDataSource(System.getenv("FILE_TEST_JDBC_URL"), "workflow", System.getenv("FILE_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        manager = new DataSourceTransactionManager(source);
        Flyway.configure().dataSource(source).load().migrate();
    }

    @BeforeEach void isolatedDirectory() { root = directory.resolve(UUID.randomUUID().toString()); }

    private AuditService audit(User actor) {
        var audit = mock(AuditService.class);
        doAnswer(call -> {
            jdbc.update("insert into audit_event(id,actor,action,resource,workspace,request_id,resource_type,details) values (?,?,?,?,?,?,?,cast(? as jsonb))",
                UUID.randomUUID(), actor.sub(), call.getArgument(0), call.getArgument(2), call.getArgument(3), UUID.randomUUID().toString(),
                call.getArgument(1), new ObjectMapper().writeValueAsString(call.getArgument(4)));
            return null;
        }).when(audit).record(anyString(), anyString(), anyString(), anyString(), anyMap());
        return audit;
    }

    private LocalFileService service(User actor) { return service(actor, audit(actor)); }
    private LocalFileService service(User actor, AuditService audit) {
        return new LocalFileService(root, jdbc, actor, audit, List.of(policy), manager);
    }
    private StoredFile store() { return service(owner).store("lab-a", "example.txt", "text/plain", input("hello")); }
    private static ByteArrayInputStream input(String text) { return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)); }
    private void denied(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(CoreException.class, action).code());
    }
    private Path blob(StoredFile file) {
        UUID key = jdbc.queryForObject("select storage_key from file_object where id=?", UUID.class, file.id());
        return root.resolve(key.toString());
    }
    private long blobCount() throws IOException { try (var files = Files.list(root)) { return files.count(); } }
    private int rowCount() { return jdbc.queryForObject("select count(*) from file_object", Integer.class); }

    @Test void uploadDownloadMetadataAndPrivatePermissions() throws Exception {
        var content = spy(input("hello"));
        var stored = service(owner).store("lab-a", "  report.TXT  ", "text/plain", content);
        assertEquals("report.TXT", stored.originalName());
        assertEquals("lab-a", stored.workspace());
        assertEquals("owner", stored.owner());
        assertEquals(5, stored.size());
        assertNotNull(stored.createdAt());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("hello".getBytes(StandardCharsets.UTF_8))), stored.checksum());
        assertEquals(stored, service(owner).describe("lab-a", stored.id()));
        try (var download = service(peer).open("lab-a", stored.id())) { assertEquals("hello", new String(download.readAllBytes(), StandardCharsets.UTF_8)); }
        verify(content, never()).close();
        assertNotEquals(stored.originalName(), blob(stored).getFileName().toString());
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(blob(stored)));
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(root));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where resource=? and actor='owner' and workspace='lab-a' and action='file.store'",
            Integer.class, stored.id().toString()));
    }

    @Test void allAllowedTypesHaveMatchingExtensionsAndSignatures() throws Exception {
        var png = new ByteArrayOutputStream();
        var jpeg = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", png));
        assertTrue(ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "jpg", jpeg));
        Map<String, byte[]> examples = Map.of("text/plain", "text".getBytes(StandardCharsets.UTF_8), "text/markdown", "# note".getBytes(StandardCharsets.UTF_8),
            "application/pdf", "%PDF-1.7\nexample\n%%EOF\n".getBytes(StandardCharsets.US_ASCII), "image/png", png.toByteArray(), "image/jpeg", jpeg.toByteArray());
        Map<String, String> names = Map.of("text/plain", "a.txt", "text/markdown", "a.md", "application/pdf", "a.pdf", "image/png", "a.png", "image/jpeg", "a.jpeg");
        for (var example : examples.entrySet()) {
            var stored = service(owner).store("lab-a", names.get(example.getKey()), example.getKey(), new ByteArrayInputStream(example.getValue()));
            try (var download = service(owner).open("lab-a", stored.id())) { assertArrayEquals(example.getValue(), download.readAllBytes()); }
        }
        assertEquals(5, blobCount());
    }

    @Test void emptyAndExactLimitSucceedButOversizeDoesNotPersist() throws Exception {
        var empty = service(owner).store("lab-a", "empty.txt", "text/plain", input(""));
        assertEquals(0, empty.size());
        byte[] bytes = new byte[(int) FileService.MAX_SIZE_BYTES];
        Arrays.fill(bytes, (byte) 'a');
        var exact = service(owner).store("lab-a", "limit.txt", "text/plain", new ByteArrayInputStream(bytes));
        assertEquals(FileService.MAX_SIZE_BYTES, exact.size());
        int rows = rowCount();
        denied(ErrorCode.PAYLOAD_TOO_LARGE, () -> service(owner).store("lab-a", "large.txt", "text/plain", new ByteArrayInputStream(new byte[bytes.length + 1])));
        assertEquals(rows, rowCount());
        assertEquals(2, blobCount());
    }

    @Test void traversalAndHeaderInjectionNamesAreRejectedBeforeWriting() throws Exception {
        var files = service(owner);
        int rows = rowCount();
        for (String name : Arrays.asList(null, "", " ", ".", "..", "../a.txt", "folder/a.txt", "C:\\a.txt", "a\r\n.txt", "a\u202e.txt", "a".repeat(201)))
            denied(ErrorCode.VALIDATION_FAILED, () -> files.store("lab-a", name, "text/plain", input("safe")));
        assertEquals(rows, rowCount());
        assertEquals(0, blobCount());
    }

    @Test void typesExtensionsBinaryTextAndTruncatedSignaturesAreRejected() throws Exception {
        var files = service(owner);
        for (String type : Arrays.asList(null, "application/octet-stream", "text/html", "image/svg+xml", "text/plain; charset=utf-8"))
            denied(ErrorCode.UNSUPPORTED_MEDIA_TYPE, () -> files.store("lab-a", "a.txt", type, input("safe")));
        denied(ErrorCode.UNSUPPORTED_MEDIA_TYPE, () -> files.store("lab-a", "a.exe", "text/plain", input("safe")));
        denied(ErrorCode.UNSUPPORTED_MEDIA_TYPE, () -> files.store("lab-a", "a.pdf", "application/pdf", input("not a PDF")));
        denied(ErrorCode.UNSUPPORTED_MEDIA_TYPE, () -> files.store("lab-a", "a.pdf", "application/pdf", input("%PDF-")));
        denied(ErrorCode.UNSUPPORTED_MEDIA_TYPE, () -> files.store("lab-a", "a.png", "image/png", new ByteArrayInputStream(HexFormat.of().parseHex("89504e470d0a1a0a"))));
        denied(ErrorCode.UNSUPPORTED_MEDIA_TYPE, () -> files.store("lab-a", "a.jpg", "image/jpeg", new ByteArrayInputStream(HexFormat.of().parseHex("ffd8ff"))));
        denied(ErrorCode.UNSUPPORTED_MEDIA_TYPE, () -> files.store("lab-a", "a.txt", "text/plain", new ByteArrayInputStream(new byte[] {0})));
        denied(ErrorCode.UNSUPPORTED_MEDIA_TYPE, () -> files.store("lab-a", "a.txt", "text/plain", new ByteArrayInputStream(new byte[] {(byte) 0xff})));
        denied(ErrorCode.VALIDATION_FAILED, () -> files.store("lab-a", "a.txt", "text/plain", null));
        assertEquals(0, blobCount());
    }

    @Test void unauthorizedUploadsDoNotReadTheInput() throws Exception {
        var content = mock(InputStream.class);
        denied(ErrorCode.FORBIDDEN, () -> service(foreign).store("lab-a", "a.txt", "text/plain", content));
        verifyNoInteractions(content);
        assertEquals(0, blobCount());
    }

    @Test void workspaceAndIdentifierIsolationAppliesToEveryOperation() {
        var stored = store();
        denied(ErrorCode.FORBIDDEN, () -> service(foreign).describe("lab-a", stored.id()));
        denied(ErrorCode.NOT_FOUND, () -> service(foreign).describe("lab-b", stored.id()));
        denied(ErrorCode.NOT_FOUND, () -> service(foreign).open("lab-b", stored.id()));
        denied(ErrorCode.NOT_FOUND, () -> service(foreign).link("lab-b", stored.id(), "test.resource", "R-1"));
        denied(ErrorCode.NOT_FOUND, () -> service(owner).open("lab-a", UUID.randomUUID()));
        denied(ErrorCode.VALIDATION_FAILED, () -> service(owner).describe("lab-a", null));
    }

    @Test void linkingRequiresOwnerAndExplicitTargetAuthorization() {
        var stored = store();
        denied(ErrorCode.FORBIDDEN, () -> service(peer).link("lab-a", stored.id(), "test.resource", "R-1"));
        denied(ErrorCode.NOT_FOUND, () -> service(owner).link("lab-a", stored.id(), "missing.policy", "R-1"));
        denied(ErrorCode.NOT_FOUND, () -> service(owner).link("lab-a", stored.id(), "test.resource", "missing"));
        denied(ErrorCode.NOT_FOUND, () -> service(owner).link("lab-a", stored.id(), "test.resource", "lab-b:R-1"));
        denied(ErrorCode.VALIDATION_FAILED, () -> service(owner).link("lab-a", stored.id(), "bad/type", "R-1"));
        denied(ErrorCode.VALIDATION_FAILED, () -> service(owner).link("lab-a", stored.id(), "test.resource", " "));
        var foreignFile = service(foreign).store("lab-b", "foreign.txt", "text/plain", input("private"));
        denied(ErrorCode.NOT_FOUND, () -> service(foreign).link("lab-b", foreignFile.id(), "test.resource", "R-1"));
        assertEquals(0, jdbc.queryForObject("select count(*) from file_link where file_id in (?,?)", Integer.class, stored.id(), foreignFile.id()));
    }

    @Test void linkedFilesRequireResourceReadAndAllowAnyAuthorizedLink() throws Exception {
        var stored = store();
        service(owner).link("lab-a", stored.id(), "test.resource", "R-1");
        denied(ErrorCode.NOT_FOUND, () -> service(peer).describe("lab-a", stored.id()));
        denied(ErrorCode.NOT_FOUND, () -> service(peer).open("lab-a", stored.id()));
        assertEquals(stored, service(reviewer).describe("lab-a", stored.id()));
        try (var download = service(reviewer).open("lab-a", stored.id())) { assertEquals("hello", new String(download.readAllBytes(), StandardCharsets.UTF_8)); }
        var reader = new User("r2-reader", peer.memberships());
        denied(ErrorCode.NOT_FOUND, () -> service(reader).describe("lab-a", stored.id()));
        service(owner).link("lab-a", stored.id(), "test.resource", "R-2");
        assertEquals(stored, service(reader).describe("lab-a", stored.id()));
        var withoutPolicy = new LocalFileService(root, jdbc, owner, audit(owner), List.of(), manager);
        denied(ErrorCode.NOT_FOUND, () -> withoutPolicy.describe("lab-a", stored.id()));
    }

    @Test void existingPolicyImplementationsCannotLinkByDefault() {
        var stored = store();
        FileAccessPolicy readOnly = new FileAccessPolicy() {
            public String resourceType() { return "legacy.resource"; }
            public boolean canRead(CurrentUser user, String workspace, String id) { return true; }
        };
        var files = new LocalFileService(root, jdbc, owner, audit(owner), List.of(readOnly), manager);
        denied(ErrorCode.NOT_FOUND, () -> files.link("lab-a", stored.id(), "legacy.resource", "R-1"));
    }

    @Test void concurrentIdenticalLinksAreIdempotentAndAuditedOnce() throws Exception {
        var stored = store();
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var futures = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                service(owner).link("lab-a", stored.id(), "test.resource", "R-1");
                return true;
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (var future : futures) assertTrue(future.get(30, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
        assertEquals(1, jdbc.queryForObject("select count(*) from file_link where file_id=?", Integer.class, stored.id()));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where resource=? and action='file.link'", Integer.class, stored.id().toString()));
    }

    @Test void auditFailureRollsBackFileMetadataContentAndAudit() throws Exception {
        var failing = audit(owner);
        doAnswer(call -> {
            jdbc.update("insert into audit_event(id,actor,action,resource,workspace,request_id,resource_type) values (?,'owner','file.failure','failure','lab-a',?,'file')",
                UUID.randomUUID(), UUID.randomUUID().toString());
            throw new IllegalStateException("Audit unavailable");
        }).when(failing).record(anyString(), anyString(), anyString(), anyString(), anyMap());
        int before = rowCount();
        assertThrows(IllegalStateException.class, () -> service(owner, failing).store("lab-a", "a.txt", "text/plain", input("hello")));
        assertEquals(before, rowCount());
        assertEquals(0, blobCount());
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_event where action='file.failure'", Integer.class));
    }

    @Test void databaseFailureAfterFileWriteRemovesContent() throws Exception {
        var invalidOwner = new User("x".repeat(101), owner.memberships());
        var audit = audit(invalidOwner);
        int before = rowCount();
        assertThrows(DataIntegrityViolationException.class, () -> service(invalidOwner, audit).store("lab-a", "a.txt", "text/plain", input("hello")));
        assertEquals(before, rowCount());
        assertEquals(0, blobCount());
        verifyNoInteractions(audit);
    }

    @Test void callerTransactionRollbackAlsoRemovesContentAndAudit() throws Exception {
        var captured = new java.util.ArrayList<StoredFile>();
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            var stored = store();
            captured.add(stored);
            assertTrue(Files.exists(blob(stored)));
            throw new IllegalStateException("Caller rolled back");
        }));
        assertEquals(0, blobCount());
        assertEquals(0, jdbc.queryForObject("select count(*) from file_object where id=?", Integer.class, captured.get(0).id()));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_event where resource=?", Integer.class, captured.get(0).id().toString()));
    }

    @Test void failedLinkAuditRollsBackLinkButKeepsExistingContent() throws Exception {
        var stored = store();
        var failing = mock(AuditService.class);
        doThrow(new IllegalStateException("Audit unavailable")).when(failing).record(anyString(), anyString(), anyString(), anyString(), anyMap());
        assertThrows(IllegalStateException.class, () -> service(owner, failing).link("lab-a", stored.id(), "test.resource", "R-1"));
        assertEquals(0, jdbc.queryForObject("select count(*) from file_link where file_id=?", Integer.class, stored.id()));
        assertTrue(Files.exists(blob(stored)));
        assertEquals(stored, service(peer).describe("lab-a", stored.id()));
    }

    @Test void inputFailureDoesNotPersistAndNeverClosesCallerStream() throws Exception {
        var input = new InputStream() {
            public int read() throws IOException { throw new IOException("Input failed"); }
        };
        int before = rowCount();
        assertThrows(IllegalStateException.class, () -> service(owner).store("lab-a", "a.txt", "text/plain", input));
        assertEquals(before, rowCount());
        assertEquals(0, blobCount());
    }

    @Test void filesystemFailureCannotLeaveMetadata() throws Exception {
        var files = service(owner);
        Files.delete(root);
        Files.writeString(root, "not a directory");
        int before = rowCount();
        assertThrows(IllegalStateException.class, () -> files.store("lab-a", "a.txt", "text/plain", input("hello")));
        assertEquals(before, rowCount());
        assertEquals("not a directory", Files.readString(root));
    }

    @Test void missingCorruptAndSymlinkedBlobsAreNotServed() throws Exception {
        var stored = store();
        var files = service(owner);
        Path blob = blob(stored);
        Files.writeString(blob, "wrong size");
        assertThrows(IllegalStateException.class, () -> files.open("lab-a", stored.id()));
        Files.writeString(blob, "other");
        assertThrows(IllegalStateException.class, () -> files.open("lab-a", stored.id()));
        Files.delete(blob);
        assertThrows(IllegalStateException.class, () -> files.open("lab-a", stored.id()));
        Path outside = directory.resolve("outside.txt");
        Files.writeString(outside, "hello");
        Files.createSymbolicLink(blob, outside);
        assertThrows(IllegalStateException.class, () -> files.open("lab-a", stored.id()));
        assertEquals("hello", Files.readString(outside));
    }

    @Test void rootSymlinksDuplicatePoliciesAndWrongDatasourceFailClosed() throws Exception {
        Path actual = Files.createDirectory(directory.resolve("actual"));
        Files.createSymbolicLink(root, actual);
        assertThrows(IllegalStateException.class, () -> service(owner));
        assertThrows(IllegalArgumentException.class, () -> new LocalFileService(actual, jdbc, owner, audit(owner), List.of(policy, policy), manager));
        var wrongManager = new DataSourceTransactionManager(new DriverManagerDataSource());
        assertThrows(IllegalArgumentException.class, () -> new LocalFileService(actual, jdbc, owner, audit(owner), List.of(policy), wrongManager));
        Files.setPosixFilePermissions(actual, PosixFilePermissions.fromString("rwxr-xr-x"));
        assertThrows(IllegalStateException.class, () -> new LocalFileService(actual, jdbc, owner, audit(owner), List.of(policy), manager));
    }

    @Test void unknownCommitOutcomeDoesNotDeletePossiblyCommittedContent() throws Exception {
        var uncertain = new DataSourceTransactionManager(source) {
            @Override protected void doCommit(DefaultTransactionStatus status) {
                super.doCommit(status);
                throw new TransactionSystemException("Connection lost after commit");
            }
        };
        var files = new LocalFileService(root, jdbc, owner, audit(owner), List.of(policy), uncertain);
        String name = UUID.randomUUID() + ".txt";
        assertThrows(TransactionSystemException.class, () -> files.store("lab-a", name, "text/plain", input("hello")));
        UUID id = jdbc.queryForObject("select id from file_object where original_name=?", UUID.class, name);
        assertTrue(Files.exists(blob(service(owner).describe("lab-a", id))));
        try (var download = service(owner).open("lab-a", id)) { assertEquals("hello", new String(download.readAllBytes(), StandardCharsets.UTF_8)); }
    }

    @Test void migrationBackfillsLegacyNameWithoutRewritingAppliedMigrations() {
        Flyway.configure().dataSource(source).schemas("file_migration_probe").target("2").load().migrate();
        UUID id = UUID.randomUUID();
        jdbc.update("insert into file_migration_probe.file_object(id,workspace,owner,storage_key,media_type,byte_size,checksum) values (?,'lab-a','owner',?,'text/plain',0,?)",
            id, UUID.randomUUID(), "0".repeat(64));
        Flyway.configure().dataSource(source).schemas("file_migration_probe").load().migrate();
        assertEquals("download", jdbc.queryForObject("select original_name from file_migration_probe.file_object where id=?", String.class, id));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into file_migration_probe.file_object(id,workspace,owner,storage_key,media_type,byte_size,checksum) values (?,'lab-a','owner',?,'text/plain',0,?)",
            UUID.randomUUID(), UUID.randomUUID(), "0".repeat(64)));
    }
}
