package vn.haui.dx.core.data.internal;

import java.util.Map;
import java.util.UUID;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.common.PageRequest;
import vn.haui.dx.core.data.api.FileService;
import vn.haui.dx.core.data.api.MetadataQuery;
import vn.haui.dx.core.data.api.MetadataRecord;
import vn.haui.dx.core.data.api.MetadataService;
import vn.haui.dx.core.data.api.NewMetadata;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import static vn.haui.dx.core.identity.api.WorkspaceRole.AGENT;
import static vn.haui.dx.core.identity.api.WorkspaceRole.COORDINATOR;

@Service
public class JdbcMetadataService implements MetadataService {
    private final MetadataRepository repository;
    private final WorkspaceAccess access;
    private final AuditService audit;
    private final Validator validator;
    private final ObjectProvider<FileService> files;
    private final MetadataReadPolicy reads;

    public JdbcMetadataService(MetadataRepository repository, WorkspaceAccess access, AuditService audit,
            Validator validator, ObjectProvider<FileService> files, MetadataReadPolicy reads) {
        this.repository = repository;
        this.access = access;
        this.audit = audit;
        this.validator = validator;
        this.files = files;
        this.reads = reads;
    }

    @Override
    @Transactional
    public MetadataRecord create(String workspace, NewMetadata input) {
        var user = member(workspace);
        if (!user.hasRole(workspace, AGENT, COORDINATOR)) throw CoreException.forbidden("Metadata creation requires agent or coordinator");
        if (input == null || !validator.validate(input).isEmpty()) throw CoreException.validation("Invalid metadata fields");
        text(input.title(), 200);
        text(input.source(), 1000);
        text(input.version(), 100);
        text(input.usageRights(), 200);
        if ((input.fileId() == null) == (input.checksum() == null))
            throw CoreException.validation("Supply exactly one of checksum or fileId");
        String checksum = input.checksum();
        FileService fileService = null;
        if (input.fileId() != null) {
            fileService = files.getIfAvailable();
            if (fileService == null) throw CoreException.conflict("File storage is not enabled");
            var file = fileService.describe(workspace, input.fileId());
            if (!user.sub().equals(file.owner())) throw CoreException.notFound("File not found or not permitted");
            checksum = file.checksum();
        }
        var record = repository.insert(workspace, user.sub(), input, checksum);
        if (fileService != null) fileService.link(workspace, input.fileId(), "metadata", record.id().toString());
        audit.record("metadata.create", "metadata", record.id().toString(), workspace, Map.of());
        return record;
    }

    @Override
    @Transactional(readOnly = true)
    public MetadataRecord get(String workspace, UUID id) {
        var user = member(workspace);
        if (id == null) throw CoreException.validation("metadata id is required");
        return repository.find(workspace, id).filter(record -> reads.canRead(user, record))
            .orElseThrow(() -> CoreException.notFound("Metadata not found"));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<MetadataRecord> search(String workspace, MetadataQuery query, PageRequest page) {
        var user = member(workspace);
        if (query == null || page == null) throw CoreException.validation("query and page are required");
        if (query.q() != null) text(query.q(), 200);
        if (query.source() != null) text(query.source(), 1000);
        return repository.search(workspace, query, page, reads.canReadInternal(user, workspace));
    }

    private CurrentUser member(String workspace) {
        var user = access.current();
        if (workspace == null || !user.isMember(workspace)) throw CoreException.forbidden("Workspace membership required");
        return user;
    }

    private static void text(String value, int max) {
        if (value == null || value.length() > max || value.codePoints().anyMatch(Character::isISOControl))
            throw CoreException.validation("Invalid metadata text");
    }
}
