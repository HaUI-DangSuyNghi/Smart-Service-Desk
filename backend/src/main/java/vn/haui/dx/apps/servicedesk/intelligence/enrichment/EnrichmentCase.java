package vn.haui.dx.apps.servicedesk.intelligence.enrichment;

import vn.haui.dx.apps.servicedesk.intelligence.context.ContextReadinessView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EnrichmentCase(
        UUID ticketId, String workspace, long contextRevision, String contextDigest, String policyDigest,
        String status, int roundCount, boolean manualRequired, boolean lateAnswer,
        Instant deadlineAt, Instant reminderDueAt, Instant remindedAt, long version,
        List<String> reasonCodes, ContextReadinessView readiness) {

    public EnrichmentCase { reasonCodes = List.copyOf(reasonCodes); }
}
