package vn.haui.dx.core.data.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.data.api.FileAccessPolicy;
import vn.haui.dx.core.data.api.FileService;
import vn.haui.dx.core.data.api.StoredFile;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.WorkspaceAccess;

@Service
public final class LocalFileService implements FileService {
    private static final Logger LOG = LoggerFactory.getLogger(LocalFileService.class);
    private final Path root;
    private final JdbcTemplate jdbc;
    private final WorkspaceAccess access;
    private final AuditService audit;
    private final Map<String, FileAccessPolicy> policies;
    private final TransactionTemplate transaction;

    @Autowired
    public LocalFileService(
            @Value("${dx.storage.root:./.local/files}") String rootPath,
            JdbcTemplate jdbc, WorkspaceAccess access, AuditService audit,
            Collection<FileAccessPolicy> policies, PlatformTransactionManager manager) {
        this(Path.of(rootPath), jdbc, access, audit, policies, manager);
    }

    public LocalFileService(Path root, JdbcTemplate jdbc, WorkspaceAccess access, AuditService audit,
            Collection<FileAccessPolicy> policies, PlatformTransactionManager manager) {
        if (!(manager instanceof DataSourceTransactionManager jdbcManager) || jdbcManager.getDataSource() != jdbc.getDataSource())
            throw new IllegalArgumentException("File metadata and transactions must share a JDBC datasource");
        this.root = root.toAbsolutePath().normalize();
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.transaction = new TransactionTemplate(manager);
        Map<String, FileAccessPolicy> registered = new HashMap<>();
        for (var policy : policies) {
            resourceType(policy.resourceType());
            if (registered.putIfAbsent(policy.resourceType(), policy) != null)
                throw new IllegalArgumentException("Duplicate file access policy");
        }
        this.policies = Map.copyOf(registered);
        try {
            Files.createDirectories(this.root, permissions("rwx------"));
            safeRoot();
        } catch (IOException exception) { throw new IllegalStateException("File storage initialization failed", exception); }
    }

    @Override
    public StoredFile store(String workspace, String originalName, String mediaType, InputStream content) {
        var user = member(workspace);
        String name = filename(originalName);
        if (content == null) throw CoreException.validation("File content is required");
        if (mediaType == null || !ALLOWED_MEDIA_TYPES.contains(mediaType)) throw unsupported();
        byte[] bytes;
        try { bytes = content.readNBytes((int) MAX_SIZE_BYTES + 1); }
        catch (IOException exception) { throw new IllegalStateException("File input could not be read", exception); }
        if (bytes.length > MAX_SIZE_BYTES) throw new CoreException(ErrorCode.PAYLOAD_TOO_LARGE, "File exceeds 5 MiB");
        validateContent(name, mediaType, bytes);
        UUID id = UUID.randomUUID();
        UUID storageKey = UUID.randomUUID();
        String checksum = checksum(bytes);
        return transaction.execute(status -> {
            write(storageKey, bytes);
            jdbc.update("insert into file_object(id,workspace,owner,storage_key,media_type,byte_size,checksum,original_name) values (?,?,?,?,?,?,?,?)",
                id, workspace, user.sub(), storageKey, mediaType, bytes.length, checksum, name);
            audit.record("file.upload", "file", id.toString(), workspace,
                Map.of("mediaType", mediaType, "size", bytes.length, "checksum", checksum));
            return row(workspace, id, "").file();
        });
    }

    @Override
    public StoredFile describe(String workspace, UUID fileId) {
        var user = member(workspace);
        return transaction.execute(status -> readable(user, workspace, fileId, " for share").file());
    }

    @Override
    public InputStream open(String workspace, UUID fileId) {
        var user = member(workspace);
        return transaction.execute(status -> {
            var row = readable(user, workspace, fileId, " for share");
            safeRoot();
            Path path = path(row.storageKey());
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalStateException("Stored content is missing or not a regular file");
            try (var content = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = content.readNBytes((int) MAX_SIZE_BYTES + 1);
                if (bytes.length != row.file().size() || !checksum(bytes).equals(row.file().checksum()))
                    throw new IOException("Stored content differs from metadata");
                return new ByteArrayInputStream(bytes);
            } catch (IOException exception) { throw new IllegalStateException("File storage read failed", exception); }
        });
    }

    @Override
    public void link(String workspace, UUID fileId, String resourceType, String resourceId) {
        var user = member(workspace);
        resourceType(resourceType);
        text(resourceId, 200, "resourceId");
        transaction.executeWithoutResult(status -> {
            var row = readable(user, workspace, fileId, " for update");
            if (!user.sub().equals(row.file().owner())) throw CoreException.forbidden("Only the file owner may attach it");
            var policy = policies.get(resourceType);
            if (policy == null || !policy.canLink(user, workspace, resourceId))
                throw CoreException.notFound("Link target not found or not permitted");
            int inserted = jdbc.update("insert into file_link(file_id,resource_type,resource_id) values (?,?,?) on conflict do nothing",
                fileId, resourceType, resourceId);
            if (inserted != 0) audit.record("file.link", "file", fileId.toString(), workspace,
                Map.of("resourceType", resourceType, "resourceId", resourceId));
        });
    }

    private record Row(StoredFile file, UUID storageKey) {}

    private Row row(String workspace, UUID fileId, String lock) {
        if (fileId == null) throw CoreException.validation("fileId is required");
        var rows = jdbc.query("select * from file_object where id=? and workspace=?" + lock, (result, index) ->
            new Row(new StoredFile(result.getObject("id", UUID.class), result.getString("workspace"), result.getString("original_name"),
                result.getString("media_type"), result.getLong("byte_size"), result.getString("checksum"), result.getString("owner"),
                result.getTimestamp("created_at").toInstant()), result.getObject("storage_key", UUID.class)), fileId, workspace);
        if (rows.isEmpty()) throw CoreException.notFound("File not found");
        return rows.get(0);
    }

    private Row readable(CurrentUser user, String workspace, UUID fileId, String lock) {
        var row = row(workspace, fileId, lock);
        var links = jdbc.queryForList("select resource_type,resource_id from file_link where file_id=?", fileId);
        if (!links.isEmpty() && links.stream().noneMatch(link -> {
            var policy = policies.get(link.get("resource_type"));
            return policy != null && policy.canRead(user, workspace, (String) link.get("resource_id"));
        })) throw CoreException.notFound("File not found");
        return row;
    }

    private void write(UUID key, byte[] bytes) {
        safeRoot();
        try (var channel = FileChannel.open(path(key),
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS), permissions("rw-------"))) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_UNKNOWN) {
                        LOG.error("Unknown commit outcome for file {}; retain content for reconciliation", key);
                    } else {
                        try {
                            if (status == STATUS_ROLLED_BACK || Boolean.FALSE.equals(jdbc.queryForObject(
                                    "select exists(select 1 from file_object where storage_key=?)", Boolean.class, key))) {
                                safeRoot();
                                Files.deleteIfExists(path(key));
                            }
                        }
                        catch (IOException | RuntimeException exception) {
                            LOG.error("Failed to reconcile completed file {}; retain for private storage reconciliation", key, exception);
                        }
                    }
                }
            });
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        } catch (IOException exception) { throw new IllegalStateException("File storage write failed", exception); }
    }

    private Path path(UUID key) { return root.resolve(key.toString()); }

    private void safeRoot() {
        try {
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(root))
                throw new IOException("Storage root is not a real directory");
            if (root.getFileSystem().supportedFileAttributeViews().contains("posix")
                    && !Files.getPosixFilePermissions(root).equals(PosixFilePermissions.fromString("rwx------")))
                throw new IOException("Storage root must have owner-only permissions");
        } catch (IOException exception) { throw new IllegalStateException("File storage root unavailable", exception); }
    }

    private FileAttribute<?>[] permissions(String mode) {
        return root.getFileSystem().supportedFileAttributeViews().contains("posix")
            ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(mode))}
            : new FileAttribute<?>[0];
    }

    private CurrentUser member(String workspace) {
        var user = access.current();
        if (workspace == null || !user.isMember(workspace)) throw CoreException.forbidden("Workspace membership required");
        return user;
    }

    private static String filename(String name) {
        if (name == null) throw CoreException.validation("originalName is required");
        name = name.strip();
        text(name, 200, "originalName");
        if (name.equals(".") || name.equals("..") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0)
            throw CoreException.validation("originalName must not contain a path");
        return name;
    }

    private static void text(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max
                || value.codePoints().anyMatch(code -> Character.isISOControl(code) || Character.getType(code) == Character.FORMAT))
            throw CoreException.validation("Invalid " + field);
    }

    private static void resourceType(String value) {
        text(value, 100, "resourceType");
        if (!value.matches("[a-z0-9-]+(?:\\.[a-z0-9-]+)*")) throw CoreException.validation("Invalid resourceType");
    }

    private static void validateContent(String name, String type, byte[] bytes) {
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        boolean valid = switch (type) {
            case "application/pdf" -> extension.equals("pdf") && starts(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))
                && new String(bytes, Math.max(0, bytes.length - 1024), Math.min(bytes.length, 1024), StandardCharsets.ISO_8859_1)
                    .stripTrailing().endsWith("%%EOF");
            case "image/png" -> extension.equals("png") && starts(bytes, HexFormat.of().parseHex("89504e470d0a1a0a"))
                && ends(bytes, HexFormat.of().parseHex("0000000049454e44ae426082"));
            case "image/jpeg" -> Set.of("jpg", "jpeg").contains(extension) && starts(bytes, HexFormat.of().parseHex("ffd8ff"))
                && ends(bytes, HexFormat.of().parseHex("ffd9"));
            case "text/plain" -> extension.equals("txt") && utf8Text(bytes);
            case "text/markdown" -> Set.of("md", "markdown").contains(extension) && utf8Text(bytes);
            default -> false;
        };
        if (!valid) throw unsupported();
    }

    private static boolean starts(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && java.util.Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static boolean ends(byte[] bytes, byte[] suffix) {
        return bytes.length >= suffix.length && java.util.Arrays.equals(bytes, bytes.length - suffix.length, bytes.length, suffix, 0, suffix.length);
    }

    private static boolean utf8Text(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).codePoints()
                .noneMatch(code -> Character.isISOControl(code) && code != '\n' && code != '\r' && code != '\t');
        } catch (CharacterCodingException exception) { return false; }
    }

    private static CoreException unsupported() { return new CoreException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "File type, extension or signature is not allowed"); }

    private static String checksum(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
