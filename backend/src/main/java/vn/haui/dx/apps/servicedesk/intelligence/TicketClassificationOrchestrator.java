package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.intelligence.api.ClassificationCapability;
import vn.haui.dx.hpdi.intelligence.api.ClassificationInput;
import vn.haui.dx.hpdi.intelligence.api.ClassificationObservation;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/** Model-first read-only flow, with independent authorization and application guards. */
@Service
public class TicketClassificationOrchestrator {
    private final TicketService tickets;
    private final ClassificationCapability classifier;
    private final HumanContext human;
    private final ClassifierConfiguration.Settings settings;
    private final ClassifierProcessingGate processing;
    private final JsonNode policy;
    private final List<String> classes;

    public TicketClassificationOrchestrator(
            TicketService tickets,
            ClassificationCapability classifier,
            HumanContext human,
            ClassifierConfiguration.Settings settings,
            ClassifierProcessingGate processing,
            ObjectMapper json) {
        this.tickets = tickets;
        this.classifier = classifier;
        this.human = human;
        this.settings = settings;
        this.processing = processing;
        try (var stream =
                getClass()
                        .getResourceAsStream(
                                "/apps/servicedesk/intelligence/guard-policy-v0.1.json")) {
            if (stream == null) {
                throw new IllegalStateException("Classification guard policy is missing");
            }
            policy = json.readTree(stream);
            var labels = new ArrayList<String>();
            policy.path("allowedClasses").forEach(value -> labels.add(value.textValue()));
            classes = List.copyOf(labels);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Classification guard policy cannot be loaded", failure);
        }
    }

    public TicketClassificationView classify(String workspace, UUID id) {
        Ticket snapshot = tickets.get(workspace, id);
        requireIdentity(snapshot, workspace, id);
        var actor = human.currentActor(workspace);
        if (actor == null
                || !workspace.equals(actor.workspace())
                || actor.subject() == null
                || actor.subject().isBlank()) {
            throw CapabilityException.forbidden("Trusted workspace actor required");
        }
        if (settings.mode() == ClassifierConfiguration.Mode.DISABLED) {
            throw CapabilityException.notFound("Classification is disabled");
        }
        if (settings.mode() == ClassifierConfiguration.Mode.PROMOTED) {
            throw CapabilityException.forbidden(
                    "Model promotion requires a separate gold and release decision");
        }
        if (!settings.modelDigest().matches("[0-9a-f]{64}")
                || !settings.policyDigest().matches("[0-9a-f]{64}")
                || !settings.provider().matches("[A-Za-z0-9_.@-]{1,100}")) {
            throw IntelligenceFailure.configuration();
        }
        String grantDigest = processing.verify(workspace);
        var fields = new LinkedHashMap<String, String>();
        fields.put("title", snapshot.title());
        fields.put("description", snapshot.description());
        ClassificationObservation observation = null;
        String failureReason = null;
        try {
            observation =
                    classifier.observe(
                            workspace,
                            new ClassificationInput(
                                    fields,
                                    classes,
                                    settings.modelDigest(),
                                    settings.policyDigest()));
            validate(observation);
        } catch (IntelligenceFailure failure) {
            failureReason = failure.code();
        }

        Ticket current = tickets.get(workspace, id);
        requireIdentity(current, workspace, id);
        if (!actor.equals(human.currentActor(workspace))) {
            throw CapabilityException.forbidden("Actor changed during classification");
        }
        if (!snapshot.equals(current) || !grantDigest.equals(processing.verify(workspace))) {
            throw CapabilityException.conflict(
                    "Ticket or processing permission changed during classification");
        }

        var reasons = new TreeSet<String>();
        var matches = new ArrayList<Map<String, Object>>();
        for (var rule : policy.path("guards")) {
            var pattern =
                    Pattern.compile(
                            rule.path("pattern").asText(),
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            for (var field : fields.entrySet()) {
                var matcher = pattern.matcher(field.getValue());
                while (matcher.find()) {
                    matches.add(
                            Map.of(
                                    "ruleId",
                                    rule.path("id").asText(),
                                    "kind",
                                    rule.path("kind").asText(),
                                    "field",
                                    field.getKey(),
                                    "start",
                                    field.getValue().codePointCount(0, matcher.start()),
                                    "end",
                                    field.getValue().codePointCount(0, matcher.end()),
                                    "text",
                                    matcher.group()));
                    if (!"SOFT_SIGNAL".equals(rule.path("kind").asText())) {
                        reasons.add(rule.path("kind").asText());
                    }
                }
            }
        }
        boolean shadow = settings.mode() == ClassifierConfiguration.Mode.SHADOW;
        if (shadow) {
            reasons.add("SHADOW_OBSERVATION_NOT_PUBLISHED");
        } else {
            reasons.add("UNVERIFIED_MODEL_SUGGESTION");
            if (failureReason != null) {
                reasons.add(failureReason);
            } else if ("ABSTAIN".equals(observation.decision())) {
                reasons.add("MODEL_ABSTAINED");
            }
        }
        var category =
                new TicketClassificationView.Category(
                        failureReason == null ? observation.decision() : "ABSTAIN",
                        failureReason == null ? observation.classId() : null,
                        settings.provider(),
                        settings.modelDigest(),
                        settings.policyDigest(),
                        null,
                        "NOT_AVAILABLE",
                        "UNVERIFIED_BY_MODEL");
        return new TicketClassificationView(
                "model-primary-assessment@0.1.0",
                settings.mode().name(),
                shadow || failureReason == null ? "REVIEW_REQUIRED" : "DEGRADED",
                shadow ? null : category,
                new TicketClassificationView.Disposition(
                        "REVIEW_REQUIRED", List.copyOf(reasons), matches, false),
                new TicketClassificationView.IndependentTriage(null, null, null, "NOT_ASSESSED"),
                true,
                false);
    }

    private void validate(ClassificationObservation value) {
        if (value == null
                || value.operationalAuthority()
                || !settings.modelDigest().equals(value.modelDigest())
                || !settings.policyDigest().equals(value.policyDigest())
                || !"UNVERIFIED_BY_MODEL".equals(value.evidenceStatus())
                || !"NOT_AVAILABLE".equals(value.scoreSemantics())
                || !("ABSTAIN".equals(value.decision()) && value.classId() == null
                        || "PROPOSE".equals(value.decision())
                                && value.classId() != null
                                && classes.contains(value.classId()))) {
            throw IntelligenceFailure.invalid();
        }
    }

    private static void requireIdentity(Ticket ticket, String workspace, UUID id) {
        if (!workspace.equals(ticket.workspace()) || !id.equals(ticket.id())) {
            throw CapabilityException.conflict("Ticket identity changed");
        }
    }
}
