package vn.haui.dx.apps.servicedesk.intelligence.context;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public final class EnrichmentSignalPlanner {
    public record Signal(
            String questionId, String slot, String reason, String state, String question,
            String priority, List<String> requiredFor, List<ContextProjection.Evidence> evidence,
            boolean blocking) {}

    private final ContextPolicyCatalog policy;

    public EnrichmentSignalPlanner(ContextPolicyCatalog policy) {
        this.policy = policy;
    }

    public List<Signal> plan(ContextProjection context, String profile) {
        var required = policy.required(profile);
        var slots = new ArrayList<>(required);
        context.slots().forEach((slot, value) -> {
            if (List.of("CONFLICT", "AMBIGUOUS").contains(value.status()) && !slots.contains(slot)) {
                slots.add(slot);
            }
        });
        // Mâu thuẫn luôn được hỏi trước; thứ tự còn lại theo readiness profile.
        slots.sort(Comparator.comparingInt(slot -> conflict(context, slot) ? 0 : 1));
        var signals = new ArrayList<Signal>();
        for (String slot : slots) {
            if (context.usable(slot)) {
                continue;
            }
            boolean conflict = conflict(context, slot);
            String state = conflict ? context.slots().get(slot).status() : "MISSING";
            var evidence = context.slots().get(slot).evidence();
            // ID ổn định qua reassessment, giúp client không phát lại cùng câu hỏi.
            String questionId = ContextPolicyCatalog.digest(profile + ":" + slot + ":" + state).substring(0, 24);
            signals.add(new Signal(questionId, slot,
                    conflict ? "CONFIRM_CONTRADICTION" : "REQUIRED_FOR_" + profile,
                    state, policy.question(slot, conflict), "HIGH", List.of(profile), evidence, true));
        }
        return List.copyOf(signals);
    }

    private static boolean conflict(ContextProjection context, String slot) {
        var value = context.slots().get(slot);
        return value != null && List.of("CONFLICT", "AMBIGUOUS").contains(value.status());
    }
}
