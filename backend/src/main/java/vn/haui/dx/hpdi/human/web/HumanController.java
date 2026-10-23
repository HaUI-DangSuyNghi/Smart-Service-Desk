package vn.haui.dx.hpdi.human.web;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.human.api.*;
@RestController
@RequestMapping("/api/hpdi/v1/workspaces")
public class HumanController {
    private final HumanContext human;
    private final MemberDirectory directory;
    public HumanController(HumanContext human, MemberDirectory directory) {
        this.human = human; this.directory = directory;
    }
    /** Workspace của người gọi kèm tên hiển thị và vai trò. */
    @GetMapping
    public List<WorkspaceContext> workspaces() { return directory.myWorkspaces(); }
    @GetMapping("/{workspace}/actor")
    public Actor actor(@PathVariable String workspace) { return human.currentActor(workspace); }
    /** Thành viên workspace (danh bạ); chỉ thành viên workspace được xem. */
    @GetMapping("/{workspace}/members")
    public List<WorkspaceMember> members(@PathVariable String workspace) { return directory.members(workspace); }
}
