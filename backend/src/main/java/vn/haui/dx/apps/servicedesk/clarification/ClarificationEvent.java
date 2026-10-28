package vn.haui.dx.apps.servicedesk.clarification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClarificationEvent(
        UUID id,
        UUID ticketId,
        String actor,
        UUID clientAnswerId,
        long expectedRevision,
        long contextRevision,
        List<ClarificationRequest.Answer> answers,
        String payloadDigest,
        Instant createdAt) {

    public ClarificationEvent {
        answers = List.copyOf(answers);
    }
}
