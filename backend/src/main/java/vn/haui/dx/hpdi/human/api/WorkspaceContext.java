package vn.haui.dx.hpdi.human.api;
import java.util.Set;
/** Workspace membership projection suitable for a personal workspace switcher. */
public record WorkspaceContext(String workspace, Set<String> roles) {
    public WorkspaceContext { roles = Set.copyOf(roles); }
}
