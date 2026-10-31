package vn.haui.dx.hpdi.intelligence.integration;

import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.intelligence.api.AssistanceRoute;
import vn.haui.dx.hpdi.intelligence.api.AssistanceRouting;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Single-instance DEV ledger. Availability changes apply only to later requests. */
public class GovernedAssistanceRouting implements AssistanceRouting {
    private static final class Health {
        String state = "READY";
        long until;
        long minute = -1;
        long day = -1;
        int requestsMinute;
        int requestsDay;
        long tokensMinute;
        long tokensDay;
    }

    private final CloudRouteCatalog catalog;
    private final Clock clock;
    private final Map<String, Health> health = new HashMap<>();
    private final Map<String, AssistanceRoute> attempts = new java.util.LinkedHashMap<>();

    public GovernedAssistanceRouting(CloudRouteCatalog catalog, Clock clock) {
        this.catalog = catalog;
        this.clock = clock;
    }

    @Override
    public void authorize(
            String workspace,
            String subject,
            String resourceId,
            String resourceDigest,
            String questionDigest,
            Set<String> sourceDigests) {
        requireCatalog();
        var approval = catalog.approval;
        boolean actor =
                java.util.stream.StreamSupport.stream(approval.get("actors").spliterator(), false)
                        .anyMatch(value -> value.isTextual() && subject.equals(value.textValue()));
        boolean resource =
                java.util.stream.StreamSupport.stream(
                                approval.get("resources").spliterator(), false)
                        .anyMatch(
                                value ->
                                        workspace.equals(value.path("workspace").asText())
                                                && resourceId.equals(value.path("id").asText())
                                                && resourceDigest.equals(
                                                        value.path("contentDigest").asText()));
        var questions = new java.util.HashSet<String>();
        approval.get("questionDigests").forEach(value -> questions.add(value.asText()));
        var sources = new java.util.HashSet<String>();
        approval.get("sourceDigests").forEach(value -> sources.add(value.asText()));

        if (!actor
                || !resource
                || !questions.contains(questionDigest)
                || sourceDigests.isEmpty()
                || !sources.containsAll(sourceDigests)) {
            throw CapabilityException.forbidden("Synthetic cloud processing is not authorized");
        }
    }

    @Override
    public synchronized AssistanceRoute select(String policyId, String dataClass) {
        requireCatalog();
        if (!catalog.policyId.equals(policyId) || !CloudRouteCatalog.DATA_CLASS.equals(dataClass))
            throw IntelligenceFailure.configuration();

        long now = clock.instant().getEpochSecond();
        for (var route : catalog.routes) {
            var status = health.computeIfAbsent(route.id(), unused -> new Health());
            if (Set.of("DISABLED", "REVOKED").contains(status.state) || now < status.until)
                continue;
            long minute = now / 60;
            long day = now / 86400;
            if (status.minute != minute) {
                status.minute = minute;
                status.requestsMinute = 0;
                status.tokensMinute = 0;
            }
            if (status.day != day) {
                status.day = day;
                status.requestsDay = 0;
                status.tokensDay = 0;
            }
            long estimate = (long) route.maxInputBytes() + route.maxOutputTokens() + 1024;
            if (status.requestsMinute >= route.rpm()
                    || status.requestsDay >= route.rpd()
                    || status.tokensMinute + estimate > route.tpm()
                    || status.tokensDay + estimate > route.tpd()) continue;

            if (attempts.size() >= 1024) throw IntelligenceFailure.unavailable();
            status.requestsMinute++;
            status.requestsDay++;
            status.tokensMinute += estimate;
            status.tokensDay += estimate;
            var selected =
                    new AssistanceRoute(
                            route.id(),
                            policyId,
                            dataClass,
                            catalog.digest,
                            catalog.routeDigest(route),
                            catalog.approvalDigest,
                            UUID.randomUUID().toString(),
                            catalog.identity(route));
            attempts.put(selected.attemptId(), selected);
            return selected;
        }

        throw IntelligenceFailure.unavailable();
    }

    @Override
    public synchronized void validate(AssistanceRoute route) {
        requireCatalog();
        if (route == null || !route.equals(attempts.get(route.attemptId())))
            throw IntelligenceFailure.configuration();
    }

    @Override
    public synchronized void feedback(
            AssistanceRoute route, String state, String code, int cooldownSeconds) {
        validate(route);
        if (!Set.of("READY", "RATE_LIMITED", "UNAVAILABLE", "DISABLED", "REVOKED").contains(state)
                || cooldownSeconds < 0
                || cooldownSeconds > 3600) throw IntelligenceFailure.invalid();
        attempts.remove(route.attemptId());
        var status = health.get(route.routeId());
        status.state = state;
        status.until = clock.instant().getEpochSecond() + cooldownSeconds;
    }

    private void requireCatalog() {
        if (catalog == null) throw IntelligenceFailure.configuration();
        catalog.validateCurrent();
    }

    @Override
    public synchronized String evidenceMode(AssistanceRoute route) {
        validate(route);
        return catalog.evidenceMode;
    }

    @Override
    public synchronized void release(AssistanceRoute route) {
        if (route != null) attempts.remove(route.attemptId());
    }
}
