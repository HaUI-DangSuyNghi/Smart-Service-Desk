package vn.haui.dx.core.data.internal;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import vn.haui.dx.core.data.api.MetadataRecord;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.FreshAdminAuthorization;
import vn.haui.dx.core.identity.api.WorkspaceDirectory;
import static vn.haui.dx.core.identity.api.WorkspaceRole.AGENT;
import static vn.haui.dx.core.identity.api.WorkspaceRole.COORDINATOR;

/** Một chính sách đọc cho metadata, nội dung tri thức và file gốc. */
@Component
public class MetadataReadPolicy {
    // Giữ tương thích với các nhãn nội bộ đã nhập trước khi form có lựa chọn cố định.
    static final List<String> INTERNAL_VALUES = List.of(
        "internal", "private", "nội bộ", "noi bo", "internal only", "chỉ dùng nội bộ");
    private final WorkspaceDirectory directory;
    private final FreshAdminAuthorization admin;

    public MetadataReadPolicy(WorkspaceDirectory directory, FreshAdminAuthorization admin) {
        this.directory = directory;
        this.admin = admin;
    }

    static boolean isInternal(String usageRights) {
        return usageRights != null && INTERNAL_VALUES.contains(usageRights.trim().toLowerCase(Locale.ROOT));
    }

    public boolean canRead(CurrentUser user, MetadataRecord record) {
        return user.isMember(record.workspace()) && (!isInternal(record.usageRights())
            || canReadInternal(user, record.workspace()));
    }

    public boolean canReadInternal(CurrentUser user, String workspace) {
        if (!user.isMember(workspace)) return false;
        boolean signedRole = user.hasRole(workspace, AGENT, COORDINATOR);
        if (!signedRole && !user.platformAdmin()) return false;
        var current = directory.members(workspace).stream()
            .filter(member -> member.sub().equals(user.sub())).findFirst();
        if (current.isEmpty()) return false;
        // Danh bạ chỉ thu hẹp quyền trong token; không cấp vai trò mới từ danh bạ.
        if ((user.hasRole(workspace, AGENT) && current.get().roles().contains(AGENT))
                || (user.hasRole(workspace, COORDINATOR) && current.get().roles().contains(COORDINATOR))) return true;
        if (!user.platformAdmin()) return false;
        return user.sub().equals(admin.requirePlatformAdmin());
    }
}
