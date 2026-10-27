package vn.haui.dx.apps.servicedesk.intelligence.duplicate;

import org.springframework.stereotype.Component;

import vn.haui.dx.apps.servicedesk.intelligence.context.ContextProjection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Quyết định advisory thuần túy: không DB, không model, không link hoặc gộp ticket. */
@Component
public final class DuplicateDecisionEngine {
    public enum Decision {
        SAME_TICKET_ID, MATCH_READY_FOR_REVIEW, POSSIBLE_DUPLICATE,
        RELATED_INCIDENT_POSSIBLE, DISTINCT, INSUFFICIENT_EVIDENCE
    }

    public record Input(
            String workspace, UUID ticketId, Instant createdAt, ContextProjection context,
            String category, boolean categoryVerified, boolean open) {}

    public record Result(
            Decision decision, List<String> matched, List<String> conflicts, List<String> missing,
            Double confidence, String rankScoreSemantics) {
        public Result {
            matched = List.copyOf(matched);
            conflicts = List.copyOf(conflicts);
            missing = List.copyOf(missing);
        }
    }

    public Result decide(Input query, Input candidate) {
        var matched = new ArrayList<String>();
        var conflicts = new ArrayList<String>();
        var missing = new ArrayList<String>();
        if (!query.workspace().equals(candidate.workspace())) {
            return result(Decision.INSUFFICIENT_EVIDENCE, matched, List.of("OUT_OF_SCOPE"), missing);
        }
        if (query.ticketId().equals(candidate.ticketId())) {
            return result(Decision.SAME_TICKET_ID, List.of("SAME_AUTHORIZED_ID"), conflicts, missing);
        }
        if (!candidate.open()) {
            conflicts.add("PREDECESSOR_NOT_OPEN");
        } else {
            matched.add("PREDECESSOR_OPEN");
        }
        if (candidate.createdAt().isAfter(query.createdAt())) {
            conflicts.add("NOT_A_PREDECESSOR");
        }

        compare(query.context(), candidate.context(), "location.room", "SAME_ROOM", matched, conflicts, missing);
        compare(query.context(), candidate.context(), "device.type", "SAME_OBJECT_TYPE", matched, conflicts, missing);
        compare(query.context(), candidate.context(), "device.identifier", "SAME_OBJECT", matched, conflicts, missing);
        for (var context : List.of(query.context(), candidate.context())) {
            if ("SHARED".equals(context.text("device.scope")) || "WIFI".equals(context.text("device.type"))) {
                missing.add("APPROVED_SHARED_OBJECT_MAPPING");
            }
        }
        for (String slot : List.of("location.building", "location.floor")) {
            if (query.context().usable(slot) && candidate.context().usable(slot)
                    && !query.context().value(slot).equals(candidate.context().value(slot))) {
                conflicts.add("DIFFERENT_" + slot.toUpperCase(java.util.Locale.ROOT).replace('.', '_'));
            }
        }
        if (query.category() == null || candidate.category() == null
                || !query.categoryVerified() || !candidate.categoryVerified()) {
            missing.add("VERIFIED_CATEGORY");
        } else if (!query.category().equals(candidate.category())) {
            conflicts.add("DIFFERENT_CATEGORY");
        } else {
            matched.add("SAME_CATEGORY");
        }

        var querySymptoms = symptoms(query.context());
        var candidateSymptoms = symptoms(candidate.context());
        if (querySymptoms.isEmpty() || candidateSymptoms.isEmpty()) {
            missing.add("SYMPTOM_CORROBORATION");
        } else if (querySymptoms.equals(candidateSymptoms)) {
            matched.add("COMPATIBLE_SYMPTOM");
        } else if (querySymptoms.stream().noneMatch(candidateSymptoms::contains)) {
            conflicts.add("DIFFERENT_SYMPTOM");
        } else {
            missing.add("MULTIPLE_ISSUES_REQUIRE_REVIEW");
        }
        for (var context : List.of(query.context(), candidate.context())) {
            String event = context.text("problemEventStatus");
            if ("RESOLVED".equals(event) || "HYPOTHETICAL".equals(event)) {
                conflicts.add("NON_ACTIVE_EVENT");
            } else if (!"ACTIVE".equals(event)) {
                missing.add("ACTIVE_EVENT");
            }
        }

        if (!conflicts.isEmpty()) {
            return result(Decision.DISTINCT, matched, conflicts, missing);
        }
        if (missing.isEmpty()) {
            return result(Decision.MATCH_READY_FOR_REVIEW, matched, conflicts, missing);
        }
        boolean multipleRooms = multipleRooms(query.context()) || multipleRooms(candidate.context());
        if (multipleRooms && matched.contains("COMPATIBLE_SYMPTOM")) {
            return result(Decision.RELATED_INCIDENT_POSSIBLE, matched, conflicts, missing);
        }
        boolean plausible = matched.contains("SAME_ROOM")
                || matched.contains("SAME_OBJECT") || matched.contains("COMPATIBLE_SYMPTOM");
        return result(plausible ? Decision.POSSIBLE_DUPLICATE : Decision.INSUFFICIENT_EVIDENCE,
                matched, conflicts, missing);
    }

    private static void compare(
            ContextProjection first, ContextProjection second, String slot, String reason,
            List<String> matched, List<String> conflicts, List<String> missing) {
        if (!first.usable(slot) || !second.usable(slot)) {
            missing.add(slot);
        } else if (!first.value(slot).equals(second.value(slot))) {
            conflicts.add("DIFFERENT_" + slot.toUpperCase(java.util.Locale.ROOT).replace('.', '_'));
        } else {
            matched.add(reason);
        }
    }

    private static List<?> symptoms(ContextProjection context) {
        return context.value("symptomCodes") instanceof List<?> values ? values : List.of();
    }

    private static boolean multipleRooms(ContextProjection context) {
        var room = context.slots().get("location.room");
        if (room == null || !"CONFLICT".equals(room.status())) {
            return false;
        }
        // Nhiều phòng trong cùng báo cáo khác với thông tin mâu thuẫn giữa các lần bổ sung.
        var bySource = room.evidence().stream()
                .filter(evidence -> !evidence.correction() && !evidence.ruleId().startsWith("TENTATIVE_"))
                .collect(java.util.stream.Collectors.groupingBy(ContextProjection.Evidence::sourceId));
        return bySource.values().stream().anyMatch(values -> values.stream()
                .map(ContextProjection.Evidence::quote).distinct().count() > 1);
    }

    private static Result result(Decision decision, List<String> matched, List<String> conflicts, List<String> missing) {
        return new Result(decision, matched, conflicts,
                missing.stream().distinct().toList(), null, "NON_PROBABILISTIC_INTERNAL_ONLY");
    }
}
