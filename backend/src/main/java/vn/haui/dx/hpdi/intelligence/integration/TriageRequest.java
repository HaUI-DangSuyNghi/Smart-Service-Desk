package vn.haui.dx.hpdi.intelligence.integration;

import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

public record TriageRequest(String contractVersion, String requestId, String profile, Context context, Input input) {
    public record Envelope(TriageRequest request, TrustedContext trustedContext, String profileVersion) {}
    public record TrustedContext(String workspaceId, String subject) {}
    public record Context(String workspaceId) {}
    public record Input(String title, String description) {}
}
