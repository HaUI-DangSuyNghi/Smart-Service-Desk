package vn.haui.dx.hpdi.intelligence.api;

import java.util.Set;

/** Deployment-owned eligibility, immutable route binding and subsequent-request health. */
public interface AssistanceRouting {
    void authorize(
            String workspace,
            String subject,
            String resourceId,
            String resourceDigest,
            String questionDigest,
            Set<String> sourceDigests);

    AssistanceRoute select(String policyId, String dataClass);

    void validate(AssistanceRoute route);

    default String evidenceMode(AssistanceRoute route) {
        validate(route);
        return "ranked";
    }

    void feedback(AssistanceRoute route, String state, String code, int cooldownSeconds);

    void release(AssistanceRoute route);
}
