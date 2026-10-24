package vn.haui.dx.hpdi.intelligence.api;

import java.util.Map;

/** Immutable server selection; public HTTP must never deserialize this record. */
public record AssistanceRoute(
        String routeId,
        String policyId,
        String dataClass,
        String catalogDigest,
        String routeDigest,
        String approvalDigest,
        String attemptId,
        Map<String, String> expectedRuntime) {

    public AssistanceRoute {
        expectedRuntime = Map.copyOf(expectedRuntime);
    }
}
