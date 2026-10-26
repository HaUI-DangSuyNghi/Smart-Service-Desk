package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.intelligence.TicketIntelligenceView.InputEvidence;
import vn.haui.dx.apps.servicedesk.intelligence.TicketIntelligenceView.Triage;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;
import vn.haui.dx.hpdi.intelligence.api.TriageSuggestion;

/** Adds application policy validation to the capability's generic wire validation. */
final class TicketTriageProjection {
    private final ObjectMapper json;
    private final JsonNode policy;

    TicketTriageProjection(ObjectMapper json) {
        this.json = json;

        try (var stream = getClass().getResourceAsStream("/apps/servicedesk/intelligence/triage-policy.json")) {
            if (stream == null) {
                throw new IllegalStateException("Assistant triage policy is missing");
            }

            policy = json.readTree(stream);
        } catch (IOException failure) {
            throw new IllegalStateException("Assistant triage policy cannot be loaded", failure);
        }
    }

    Triage project(TriageSuggestion suggestion, Ticket ticket) {
        JsonNode source = json.valueToTree(suggestion.attributes());

        require(ticket.workspace().equals(text(source, "workspaceId")));
        require("0.1.0".equals(text(source, "contractVersion")));
        require("service-desk".equals(text(source, "profile")));

        if ("DEGRADED".equals(text(source, "status"))) {
            require(source.path("needsReview").asBoolean());
            require(source.path("result").isNull() && source.path("explanation").isNull());
            require(source.path("evidence").isArray() && source.path("evidence").isEmpty());
            return unavailable();
        }

        require("SUCCESS".equals(text(source, "status")));
        require("rules".equals(text(source.path("runtime"), "engine")));
        require("service-desk@0.3.0".equals(text(source.path("runtime"), "profileVersion")));
        require("0.3.0".equals(text(source.path("runtime"), "rulesVersion")));

        JsonNode result = source.path("result");
        JsonNode explanation = source.path("explanation");
        String category = text(result, "category");
        String priority = text(result, "priority");
        String group = text(result, "suggestedGroup");
        String reason = text(explanation, "reasonCode");
        String impact = nullableText(explanation, "impact");
        String urgency = nullableText(explanation, "urgency");

        require(policy.path("groups").has(category));
        require(group.equals(policy.path("groups").path(category).asText()));
        require(source.path("needsReview").isBoolean());

        List<String> matchedRules = rules(explanation.path("matchedRules"));
        boolean review = source.path("needsReview").booleanValue();

        if ("INSUFFICIENT_INFORMATION".equals(reason)) {
            require(review && "OTHER".equals(category) && "MEDIUM".equals(priority));
            require(impact == null && urgency == null);
            require(matchedRules.contains("REVIEW-INSUFFICIENT-001"));
        } else {
            require("CLASSIFIED".equals(reason) || "ROOT_CAUSE_CONFLICT".equals(reason));
            require(impact != null && urgency != null);
            require(priority.equals(policy.path("matrix").path(impact).path(urgency).asText()));
            require(matchedRules.contains(policy.path("categoryRules").path(category).asText()));
            require(matchedRules.contains(policy.path("impactRules").path(impact).asText()));
            require(matchedRules.contains(policy.path("urgencyRules").path(urgency).asText()));
            require(matchedRules.contains(policy.path("priorityRules").path(priority).asText()));
            require("ROOT_CAUSE_CONFLICT".equals(reason) == matchedRules.contains("CONFLICT-ROOT-001"));
        }

        List<InputEvidence> evidence = evidence(source.path("evidence"), ticket);

        return new Triage(
                "SUCCESS", category, priority, group, impact, urgency, reason,
                matchedRules, evidence, review,
                TicketExplanationRenderer.render(category, priority, impact, urgency, reason));
    }

    static Triage unavailable() {
        return new Triage(
                "DEGRADED", null, null, null, null, null,
                "INTELLIGENCE_UNAVAILABLE", List.of(), List.of(), true, null);
    }

    private List<String> rules(JsonNode values) {
        require(values.isArray() && !values.isEmpty() && values.size() <= 16);
        var rules = new ArrayList<String>();
        var seen = new HashSet<String>();

        for (JsonNode value : values) {
            require(value.isTextual() && policy.path("knownRules").has(value.textValue()));
            require(seen.add(value.textValue()));
            rules.add(value.textValue());
        }

        return List.copyOf(rules);
    }

    private static List<InputEvidence> evidence(JsonNode values, Ticket ticket) {
        require(values.isArray() && !values.isEmpty() && values.size() <= 16);
        var evidence = new ArrayList<InputEvidence>();

        for (JsonNode value : values) {
            String field = text(value, "field");
            String excerpt = text(value, "text");
            require("INPUT".equals(text(value, "type")));
            require("title".equals(field) || "description".equals(field));
            String original = "title".equals(field) ? ticket.title() : ticket.description();
            require(original.contains(excerpt));
            evidence.add(new InputEvidence(field, excerpt));
        }

        return List.copyOf(evidence);
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        require(value.isTextual() && !value.textValue().isBlank());
        return value.textValue();
    }

    private static String nullableText(JsonNode node, String key) {
        return node.path(key).isNull() ? null : text(node, key);
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw IntelligenceFailure.invalid();
        }
    }
}
