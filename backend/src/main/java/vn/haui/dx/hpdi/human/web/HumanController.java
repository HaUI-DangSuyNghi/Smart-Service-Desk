package vn.haui.dx.hpdi.human.web;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.human.api.*;
@RestController
@RequestMapping("/api/hpdi/v1/workspaces")
public class HumanController {
    private final HumanContext human;
    private final MemberDirectory directory;
    private final ActivityFeed activity;
    public HumanController(HumanContext human, MemberDirectory directory, ActivityFeed activity) {
        this.human = human; this.directory = directory; this.activity = activity;
    }
    /** Workspace của người gọi kèm tên hiển thị và vai trò. */
    @GetMapping
    public List<WorkspaceContext> workspaces() { return directory.myWorkspaces(); }
    @GetMapping("/{workspace}/actor")
    public Actor actor(@PathVariable String workspace) { return human.currentActor(workspace); }
    /** Thành viên workspace (danh bạ); chỉ thành viên workspace được xem. */
    @GetMapping("/{workspace}/members")
    public List<WorkspaceMember> members(@PathVariable String workspace) { return directory.members(workspace); }
    /** Quyền của người gọi trong workspace (từ Keycloak), kèm mô tả tiếng Việt; chỉ để hiển thị. */
    @GetMapping("/{workspace}/permissions")
    public List<WorkspacePermission> permissions(@PathVariable String workspace) {
        return directory.myPermissions(workspace);
    }
    /** Hoạt động của tôi: thao tác do chính người gọi làm trong workspace, mới nhất trước. */
    @GetMapping("/{workspace}/activity")
    public Page<ActivityEntry> activity(@PathVariable String workspace,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return activity.mine(workspace, PageRequest.of(page, size));
    }
}
