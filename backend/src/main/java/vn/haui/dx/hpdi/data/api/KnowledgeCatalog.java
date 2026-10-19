package vn.haui.dx.hpdi.data.api;
import java.util.UUID;
public interface KnowledgeCatalog { AuthorizedKnowledge resolve(String workspace, UUID metadataId); }
