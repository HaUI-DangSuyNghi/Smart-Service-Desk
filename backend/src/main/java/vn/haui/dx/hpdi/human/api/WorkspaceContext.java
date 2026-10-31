package vn.haui.dx.hpdi.human.api;
import java.util.Set;
/** Một workspace của người dùng hiện tại, cho ô chọn workspace: mã, tên hiển thị và vai trò. */
public record WorkspaceContext(String workspace, String name, Set<String> roles) {
    public WorkspaceContext { roles = Set.copyOf(roles); }
}
