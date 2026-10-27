package vn.haui.dx.apps.servicedesk.intelligence.context;

import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ContextReadinessService {
    private final ContextEvaluationGuard guard;
    private final ContextSlotExtractor extractor;
    private final ContextReadinessPolicy policy;

    public ContextReadinessService(ContextEvaluationGuard guard, ContextSlotExtractor extractor, ContextReadinessPolicy policy) {
        this.guard = guard;
        this.extractor = extractor;
        this.policy = policy;
    }

    public ContextReadinessView assess(String workspace, UUID id) {
        return guard.evaluate(workspace, id, session -> {
            if (session.shadow()) {
                // SHADOW không công bố kết luận readiness, câu hỏi hoặc digest nguồn.
                return new ContextReadinessView(ContextReadinessView.VERSION, id, session.snapshot().contextRevision(),
                        null, null, java.util.List.of(), java.util.List.of("SHADOW_NOT_PUBLISHED"), "MANUAL_REVIEW",
                        java.util.List.of(), policy.digest(), true, false, false);
            }
            return policy.evaluate(session.snapshot(), extractor.extract(session.snapshot()), 0);
        });
    }
}
