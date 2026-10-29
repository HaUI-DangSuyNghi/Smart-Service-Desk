package vn.haui.dx.apps.servicedesk.intelligence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.stereotype.Component;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.hpdi.common.api.CapabilityException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/** Explicit workstation mode. Never impersonates an externally issued processing grant. */
@Component
public final class LocalModelRuntime {
    private final boolean enabled;
    private final Set<String> workspaces;

    public LocalModelRuntime(
            @Value("${dx.ai.runtime-mode:EXTERNAL}") String mode,
            @Value("${dx.ai.local-workspaces:auto}") List<String> workspaces,
            @Value("${intelligence.classification.base-url:}") String endpoint,
            Environment environment) {
        if (!Set.of("EXTERNAL", "LOCAL_DEV").contains(mode)) {
            throw new IllegalArgumentException("DX_AI_RUNTIME_MODE must be EXTERNAL or LOCAL_DEV");
        }
        enabled = "LOCAL_DEV".equals(mode);
        this.workspaces = Set.copyOf(workspaces);
        if (enabled) {
            if (!environment.acceptsProfiles(Profiles.of("local-dev"))
                    || environment.acceptsProfiles(Profiles.of("prod", "production"))) {
                throw new IllegalStateException("LOCAL_DEV requires the local-dev Spring profile and excludes production");
            }
            URI uri = URI.create(endpoint);
            if (!"http".equals(uri.getScheme())
                    || uri.getHost() == null
                    || !Set.of("intelligence-classification", "localhost", "127.0.0.1", "[::1]").contains(uri.getHost())
                    || uri.getPort() != 8003 || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                    || this.workspaces.isEmpty()
                    || (this.workspaces.contains("auto") && this.workspaces.size() != 1)
                    || this.workspaces.stream().anyMatch(value -> !value.matches("[a-zA-Z0-9_-]{1,100}"))) {
                throw new IllegalStateException("LOCAL_DEV requires a local classifier endpoint and valid workspace configuration");
            }
        }
    }

    public static LocalModelRuntime external() {
        return new LocalModelRuntime("EXTERNAL", List.of(), "", new StandardEnvironment());
    }

    public boolean enabled() { return enabled; }
    @org.springframework.beans.factory.annotation.Value("${dx.ai.provider-label:Qwen}")
    private String providerLabel = "Qwen";
    public String providerLabel() { return providerLabel; }
    public boolean automaticWorkspaces() { return enabled && workspaces.contains("auto"); }
    public boolean supports(String workspace) {
        return enabled && workspace != null && workspace.matches("[a-zA-Z0-9_-]{1,100}")
                && (automaticWorkspaces() || workspaces.contains(workspace));
    }

    public String authorize(String workspace, String purpose, String identity) {
        if (!supports(workspace)) {
            throw CapabilityException.forbidden("Local model processing is not enabled for this workspace");
        }
        return AssessmentPolicy.sha256(("LOCAL_DEV:" + workspace + ":" + purpose + ":" + identity)
                .getBytes(StandardCharsets.UTF_8));
    }
}
