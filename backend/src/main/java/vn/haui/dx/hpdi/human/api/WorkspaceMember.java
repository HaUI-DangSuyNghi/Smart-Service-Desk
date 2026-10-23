package vn.haui.dx.hpdi.human.api;
import java.util.Set;
/** Thành viên workspace để hiển thị (danh bạ, người tạo, giao việc); không dùng để cấp quyền. */
public record WorkspaceMember(String subject, String username, String fullName, Set<String> roles) {
    public WorkspaceMember { roles = Set.copyOf(roles); }
}
