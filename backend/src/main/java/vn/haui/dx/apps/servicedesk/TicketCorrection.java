package vn.haui.dx.apps.servicedesk;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class TicketCorrection {
    private TicketCorrection() {}
    public record Input(TicketReview.Category category, TicketReview.Priority priority,
                        @NotBlank @Size(max=1000) String reason) {}
    public static Input parse(com.fasterxml.jackson.databind.JsonNode body) {
        if (body == null || !body.isObject()) throw invalid();
        body.fieldNames().forEachRemaining(field -> {
            if (!java.util.Set.of("category","priority","reason").contains(field)) throw invalid();
        });
        if (!body.path("reason").isTextual() || body.has("category") && !body.get("category").isTextual()
                || body.has("priority") && !body.get("priority").isTextual()) throw invalid();
        try {
            return new Input(body.has("category") ? TicketReview.Category.valueOf(body.get("category").textValue()) : null,
                body.has("priority") ? TicketReview.Priority.valueOf(body.get("priority").textValue()) : null, body.get("reason").textValue());
        } catch (IllegalArgumentException failure) { throw invalid(); }
    }
    private static vn.haui.dx.hpdi.common.api.CapabilityException invalid() {
        return vn.haui.dx.hpdi.common.api.CapabilityException.validation("Invalid correction fields");
    }
    public record History(UUID operationId, String processInstanceId, String reviewTaskId, String handleTaskId,
                          String field, String previousValue, String newValue, boolean fromAi,
                          String by, String reason, Instant at) {}
}
