package vn.haui.dx.core.workflow.api;

import java.util.Objects;

/**
 * Lọc inbox: ASSIGNED = task mình đang giữ; CANDIDATE = task chưa ai nhận của nhóm mình;
 * ALL = mọi task đang mở của workspace, chỉ coordinator (để giao việc).
 */
public record InboxFilter(Scope scope) {
    public enum Scope { ASSIGNED, CANDIDATE, ALL }

    public InboxFilter { Objects.requireNonNull(scope, "scope"); }
}
