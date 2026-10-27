package vn.haui.dx.apps.servicedesk.intelligence.enrichment;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Chỉ tag bằng tập outcome cố định; không có ticket/workspace/actor hoặc text. */
@Component
public class EnrichmentMetrics {
    private static final Set<String> OUTCOMES = Set.of("READY", "OPTIONAL_ENRICHMENT", "AWAITING_ANSWER",
            "MANUAL_REVIEW", "TIMED_OUT", "CANCELLED_BY_PROCESS", "STALE_CONTEXT", "AI_PROCESSING_UNAVAILABLE", "AI_PROVIDER_UNAVAILABLE");
    private final MeterRegistry meters;

    public EnrichmentMetrics(MeterRegistry meters) { this.meters = meters; }

    public void evaluated(String outcome, long nanoseconds) {
        String bounded = OUTCOMES.contains(outcome) ? outcome : "OTHER";
        meters.counter("servicedesk.enrichment.evaluations", "outcome", bounded).increment();
        meters.timer("servicedesk.enrichment.processing", "outcome", bounded)
                .record(nanoseconds, TimeUnit.NANOSECONDS);
    }
}
