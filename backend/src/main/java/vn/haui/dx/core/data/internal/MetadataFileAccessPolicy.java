package vn.haui.dx.core.data.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.haui.dx.core.data.api.FileAccessPolicy;
import vn.haui.dx.core.data.api.MetadataRecord;
import vn.haui.dx.core.identity.api.CurrentUser;
import static vn.haui.dx.core.identity.api.WorkspaceRole.AGENT;
import static vn.haui.dx.core.identity.api.WorkspaceRole.COORDINATOR;

@Component
public class MetadataFileAccessPolicy implements FileAccessPolicy {
    private final MetadataRepository repository;

    public MetadataFileAccessPolicy(MetadataRepository repository) { this.repository = repository; }
    @Override public String resourceType() { return "metadata"; }
    @Override public boolean canRead(CurrentUser user, String workspace, String resourceId) {
        return resource(user, workspace, resourceId).isPresent();
    }
    @Override public boolean canLink(CurrentUser user, String workspace, String resourceId) {
        return user.hasRole(workspace, AGENT, COORDINATOR)
            && resource(user, workspace, resourceId).filter(record -> record.owner().equals(user.sub())).isPresent();
    }

    private Optional<MetadataRecord> resource(CurrentUser user, String workspace, String resourceId) {
        if (!user.isMember(workspace) || resourceId == null) return Optional.empty();
        try { return repository.find(workspace, UUID.fromString(resourceId)); }
        catch (IllegalArgumentException exception) { return Optional.empty(); }
    }
}
