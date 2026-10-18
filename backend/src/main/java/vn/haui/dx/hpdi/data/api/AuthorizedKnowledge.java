package vn.haui.dx.hpdi.data.api;
import java.util.UUID;
public record AuthorizedKnowledge(UUID metadataId, String workspace, String title, String version,
                                  String checksum, String usageRights, String mediaType, byte[] content) {
    public AuthorizedKnowledge { content = content.clone(); }
    @Override public byte[] content() { return content.clone(); }
}
