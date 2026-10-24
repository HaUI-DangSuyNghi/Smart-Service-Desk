package vn.haui.dx.hpdi.intelligence.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Created by an authorized use case, never bound to a public HTTP body. */
public record AssistanceInput(
        String question,
        String profileId,
        String profileVersion,
        String policyIdentity,
        List<SourcePin> sources,
        Map<String, String> generationRuntime) {

    public AssistanceInput {
        sources = List.copyOf(sources);
        generationRuntime = Map.copyOf(generationRuntime);
    }

    /** Approval is an application governance concern; equality is enforced here. */
    public record SourcePin(
            UUID metadataId,
            String version,
            String checksum,
            String usageRights) {}
}
