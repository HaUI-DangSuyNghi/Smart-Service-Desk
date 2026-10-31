package vn.haui.dx.core.identity.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** Một cặp (workspace, vai trò) của người dùng. */
public record Membership(String workspace, WorkspaceRole role) {
    public static final Pattern WORKSPACE = Pattern.compile("[a-z0-9-]{1,50}");

    public Membership {
        Objects.requireNonNull(role, "role");
        if (workspace == null || !WORKSPACE.matcher(workspace).matches())
            throw new IllegalArgumentException("Invalid workspace: " + workspace);
    }
    /** Candidate group trong Flowable, ví dụ {@code lab-a:coordinator}. */
    public String candidateGroup() { return workspace + ":" + role.value(); }
}
