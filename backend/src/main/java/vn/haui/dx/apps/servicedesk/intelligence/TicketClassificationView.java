package vn.haui.dx.apps.servicedesk.intelligence;

import java.util.List;
import java.util.Map;

public record TicketClassificationView(
        String contractVersion,
        String mode,
        String status,
        Category classification,
        Disposition policyDisposition,
        IndependentTriage triage,
        boolean needsReview,
        boolean operationalAuthority) {

    public record Category(
            String decision,
            String category,
            String provider,
            String modelDigest,
            String policyDigest,
            Double score,
            String scoreSemantics,
            String evidenceStatus) {}

    public record Disposition(
            String status,
            List<String> reasonCodes,
            List<Map<String, Object>> ruleMatches,
            boolean autoRoutingAllowed) {
        public Disposition {
            reasonCodes = List.copyOf(reasonCodes);
            ruleMatches = ruleMatches.stream().map(Map::copyOf).toList();
        }
    }

    public record IndependentTriage(
            String impact, String urgency, String priority, String riskStatus) {}
}
