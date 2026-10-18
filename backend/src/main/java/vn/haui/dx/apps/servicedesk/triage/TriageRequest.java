package vn.haui.dx.apps.servicedesk.triage;

public record TriageRequest(String contractVersion, String requestId, String profile, Context context, Input input) {
    public record Context(String workspaceId) {}
    public record Input(String title, String description) {}
}
