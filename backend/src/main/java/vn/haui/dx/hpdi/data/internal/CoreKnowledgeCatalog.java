package vn.haui.dx.hpdi.data.internal;
import java.util.Set;
import java.util.UUID;
import java.util.HexFormat;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import vn.haui.dx.core.data.api.MetadataQuery;
import vn.haui.dx.core.data.api.MetadataService;
import vn.haui.dx.core.data.api.FileService;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.human.api.MemberDirectory;
import vn.haui.dx.hpdi.data.api.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;
@Service
public class CoreKnowledgeCatalog implements KnowledgeCatalog {
    /** Chỉ file văn bản mới là tri thức xem/trích dẫn được. */
    static final Set<String> TEXT_MEDIA_TYPES = Set.of("text/plain", "text/markdown");
    private final HumanContext human;
    private final MetadataService metadata;
    private final FileService files;
    private final MemberDirectory directory;
    public CoreKnowledgeCatalog(HumanContext human, MetadataService metadata, FileService files, MemberDirectory directory) {
        this.human=human; this.metadata=metadata; this.files=files; this.directory=directory;
    }
    public Page<KnowledgeSummary> search(String workspace, String q, PageRequest page) {
        human.currentActor(workspace);
        var query = new MetadataQuery(q == null || q.isBlank() ? null : q.trim(), null, "createdAt,desc", TEXT_MEDIA_TYPES);
        var found = metadata.search(workspace, query, new vn.haui.dx.core.common.PageRequest(page.page(), page.size()));
        var names = directory.displayNames(workspace);
        var items = found.items().stream().map(r -> new KnowledgeSummary(r.id(), r.workspace(), r.title(), r.source(),
            r.version(), r.usageRights(), r.owner(), names.get(r.owner()), r.createdAt())).toList();
        return new Page<>(items, found.page(), found.size(), found.total());
    }
    public KnowledgeRecord record(String workspace, UUID id) {
        human.currentActor(workspace);
        var r = metadata.get(workspace, id);
        return new KnowledgeRecord(r.id(), r.workspace(), r.title(), r.source(), r.version(), r.usageRights(),
            r.checksum(), r.fileId(), r.owner(), directory.displayNames(workspace).get(r.owner()), r.createdAt());
    }
    public AuthorizedKnowledge resolve(String workspace, UUID id) {
        human.currentActor(workspace);
        var record = metadata.get(workspace, id);
        if (record.fileId()==null) throw CapabilityException.notFound("Knowledge content unavailable");
        var file = files.describe(workspace, record.fileId());
        if (!workspace.equals(record.workspace()) || !workspace.equals(file.workspace()) || !record.checksum().equals(file.checksum()))
            throw CapabilityException.conflict("Knowledge provenance mismatch");
        if (!TEXT_MEDIA_TYPES.contains(file.mediaType()))
            throw CapabilityException.validation("Knowledge requires text content");
        try (var input = files.open(workspace, file.id())) {
            byte[] bytes = input.readNBytes((int)FileService.MAX_SIZE_BYTES+1);
            if (bytes.length!=file.size() || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(record.checksum()))
                throw CapabilityException.conflict("Knowledge content changed");
            return AuthorizedKnowledge.of(id, workspace, record.title(), record.version(), record.checksum(), record.usageRights(), file.mediaType(), bytes);
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException error) {
            throw CapabilityException.conflict("Knowledge content unavailable");
        }
    }
}
