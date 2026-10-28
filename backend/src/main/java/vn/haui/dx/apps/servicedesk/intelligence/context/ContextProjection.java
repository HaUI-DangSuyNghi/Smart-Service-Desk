package vn.haui.dx.apps.servicedesk.intelligence.context;

import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentEvidence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ContextProjection(Map<String, Slot> slots, List<String> reasons) {
    public ContextProjection {
        slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
        reasons = List.copyOf(reasons);
    }

    public record Slot(Object value, String status, List<Evidence> evidence, String policyVersion) {
        public Slot { evidence = List.copyOf(evidence); }
    }

    public record Evidence(
            String sourceId, long sourceRevision, String quote, int start, int end,
            String ruleId, boolean correction) {

        public boolean verifies(String source) {
            return new AssessmentEvidence(sourceId, quote, start, end, List.of(), "LITERAL_SPAN", ruleId)
                    .verifies(source);
        }
    }

    public Object value(String slot) {
        return slots.containsKey(slot) ? slots.get(slot).value() : null;
    }

    public String text(String slot) {
        Object value = value(slot);
        return value instanceof String text ? text : null;
    }

    public boolean usable(String slot) {
        return value(slot) != null && List.of("OBSERVED", "USER_REPORTED").contains(slots.get(slot).status());
    }
}
