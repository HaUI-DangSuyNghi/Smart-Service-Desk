package vn.haui.dx.core.workflow.api;

import java.util.Objects;

/** Lọc inbox: ASSIGNED = task mình đang giữ; CANDIDATE = task chưa ai nhận của nhóm mình. */
public record InboxFilter(Scope scope) {
    public enum Scope { ASSIGNED, CANDIDATE }

    public InboxFilter { Objects.requireNonNull(scope, "scope"); }
}
