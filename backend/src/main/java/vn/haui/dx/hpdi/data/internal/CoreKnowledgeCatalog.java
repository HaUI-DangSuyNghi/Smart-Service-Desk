package vn.haui.dx.hpdi.data.internal;
import java.util.UUID;
import java.util.HexFormat;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import vn.haui.dx.core.data.api.MetadataService;
import vn.haui.dx.core.data.api.FileService;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.data.api.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;
@Service
public class CoreKnowledgeCatalog implements KnowledgeCatalog {
    private final HumanContext human;
    private final MetadataService metadata;
    private final FileService files;
    public CoreKnowledgeCatalog(HumanContext human, MetadataService metadata, FileService files) {
        this.human=human; this.metadata=metadata; this.files=files;
    }
    public AuthorizedKnowledge resolve(String workspace, UUID id) {
        human.currentActor(workspace);
        var record = metadata.get(workspace, id);
        if (record.fileId()==null) throw CapabilityException.notFound("Knowledge content unavailable");
        var file = files.describe(workspace, record.fileId());
        if (!workspace.equals(record.workspace()) || !workspace.equals(file.workspace()) || !record.checksum().equals(file.checksum()))
            throw CapabilityException.conflict("Knowledge provenance mismatch");
        if (!java.util.Set.of("text/plain", "text/markdown").contains(file.mediaType()))
            throw CapabilityException.validation("Knowledge requires text content");
        try (var input = files.open(workspace, file.id())) {
            byte[] bytes = input.readNBytes((int)FileService.MAX_SIZE_BYTES+1);
            if (bytes.length!=file.size() || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(record.checksum()))
                throw CapabilityException.conflict("Knowledge content changed");
            return new AuthorizedKnowledge(id, workspace, record.title(), record.version(), record.checksum(), record.usageRights(), file.mediaType(), bytes);
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException error) {
            throw CapabilityException.conflict("Knowledge content unavailable");
        }
    }
}
