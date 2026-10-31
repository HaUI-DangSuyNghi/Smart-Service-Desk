package vn.haui.dx.hpdi.human.web;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.human.api.*;
@RestController
@RequestMapping("/api/hpdi/v1/workspaces")
public class HumanController {
    /** Số subject tối đa mỗi lần hỏi tên, đủ cho một trang danh sách. */
    static final int MAX_NAME_LOOKUP = 100;
    private final HumanContext human;
    private final MemberDirectory directory;
    private final ActivityFeed activity;
    private final PermissionGuard permissions;
    public HumanController(HumanContext human, MemberDirectory directory, ActivityFeed activity,
                           PermissionGuard permissions) {
        this.human = human; this.directory = directory; this.activity = activity; this.permissions = permissions;
    }
    /** Workspace của người gọi kèm tên hiển thị và vai trò. */
    @GetMapping
    public List<WorkspaceContext> workspaces() { return directory.myWorkspaces(); }
    @GetMapping("/{workspace}/actor")
    public Actor actor(@PathVariable String workspace) { return human.currentActor(workspace); }
    /** Thành viên workspace (danh bạ đầy đủ kèm vai trò); cần quyền xem thành viên. */
    @GetMapping("/{workspace}/members")
    public List<WorkspaceMember> members(@PathVariable String workspace) {
        permissions.require(workspace, Permissions.MEMBER_VIEW);
        return directory.members(workspace);
    }
    /**
     * Tên hiển thị của các subject người gọi đã thấy (người tạo, người xử lý...), để trang tiến độ, tài liệu
     * hiện tên mà không cần quyền xem cả danh bạ.
     */
    @GetMapping("/{workspace}/people")
    public Map<String, String> people(@PathVariable String workspace, @RequestParam Set<String> ids) {
        if (ids.size() > MAX_NAME_LOOKUP) throw CapabilityException.validation("Too many ids");
        human.currentActor(workspace);
        return directory.names(workspace, ids);
    }
    /** Quyền của người gọi trong workspace (từ Keycloak), kèm mô tả tiếng Việt; chỉ để hiển thị. */
    @GetMapping("/{workspace}/permissions")
    public List<WorkspacePermission> permissions(@PathVariable String workspace) {
        return directory.myPermissions(workspace);
    }
    /** Hoạt động của tôi: thao tác do chính người gọi làm trong workspace, mới nhất trước. */
    @GetMapping("/{workspace}/activity")
    public Page<ActivityEntry> activity(@PathVariable String workspace,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        permissions.require(workspace, Permissions.AUDIT_VIEW_OWN);
        return activity.mine(workspace, PageRequest.of(page, size));
    }
    /** Nhật ký của cả workspace (điều phối viên), lọc theo thời gian, thao tác, người thực hiện. */
    @GetMapping("/{workspace}/audit")
    public Page<ActivityEntry> audit(@PathVariable String workspace,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String action, @RequestParam(required = false) String actor,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        permissions.require(workspace, Permissions.AUDIT_VIEW);
        return activity.workspace(workspace, from, to, blankToNull(action), blankToNull(actor), PageRequest.of(page, size));
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
}
