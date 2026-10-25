package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.apps.servicedesk.assistance.TicketAssistanceService;
import vn.haui.dx.apps.servicedesk.intelligence.TicketIntelligenceView.Guidance;
import vn.haui.dx.apps.servicedesk.intelligence.TicketIntelligenceView.Triage;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceCapability;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;
import vn.haui.dx.hpdi.intelligence.api.TriageInput;

/** Tier 3 composition; deliberately outside a surrounding database transaction. */
@Service
public class TicketIntelligenceOrchestrator {
    private final TicketService tickets;
    private final IntelligenceCapability intelligence;
    private final TicketAssistanceService assistance;
    private final TicketTriageProjection projection;
    private final HumanContext human;

    public TicketIntelligenceOrchestrator(
            TicketService tickets,
            IntelligenceCapability intelligence,
            TicketAssistanceService assistance,
            ObjectMapper json,
            HumanContext human) {
        this.tickets = tickets;
        this.intelligence = intelligence;
        this.assistance = assistance;
        this.projection = new TicketTriageProjection(json);
        this.human = human;
    }

    public TicketIntelligenceView assess(String workspace, UUID id, TicketAssessmentRequest request) {
        Ticket snapshot = tickets.get(workspace, id);
        requireIdentity(snapshot, workspace, id);
        var actor = human.currentActor(workspace);

        if (actor == null || !workspace.equals(actor.workspace())
                || actor.subject() == null || actor.subject().isBlank()) {
            throw CapabilityException.forbidden("Trusted workspace actor required");
        }

        Triage triage = triage(snapshot);
        Guidance guidance = guidance(snapshot, triage, request);

        // Reauthorize even on the triage-only and provider-degraded paths.
        Ticket current = tickets.get(workspace, id);
        requireIdentity(current, workspace, id);

        if (!actor.equals(human.currentActor(workspace))) {
            throw CapabilityException.forbidden("Actor changed during assessment");
        }

        if (!snapshot.equals(current)) {
            throw CapabilityException.conflict("Ticket changed during assessment");
        }

        var reasons = new ArrayList<String>();

        if (triage.needsReview()) {
            reasons.add(triage.reasonCode());
        }

        switch (guidance.status()) {
            case "SUPPORTED" -> reasons.add("GUIDANCE_REQUIRES_REVIEW");
            case "UNAVAILABLE" -> reasons.add("GUIDANCE_UNAVAILABLE");
            case "INSUFFICIENT_EVIDENCE" -> reasons.add("GUIDANCE_INSUFFICIENT_EVIDENCE");
            default -> { }
        }

        String status = "COMPLETE";

        if ("DEGRADED".equals(triage.status())) {
            status = "DEGRADED";
        } else if (triage.needsReview()) {
            status = "REVIEW_REQUIRED";
        } else if (!"SUPPORTED".equals(guidance.status()) && request.includeGuidance()) {
            status = "PARTIAL";
        }

        List<String> questions = "INSUFFICIENT_INFORMATION".equals(triage.reasonCode())
                ? List.of("Thiết bị hoặc dịch vụ nào bị ảnh hưởng?",
                        "Có bao nhiêu người bị ảnh hưởng và công việc có đang bị chặn không?")
                : List.of();

        return new TicketIntelligenceView(
                "0.1.0", UUID.randomUUID().toString(), status, triage,
                new TicketIntelligenceView.Risk("NOT_ASSESSED", null, "NO_APPROVED_RISK_RUBRIC"),
                guidance, !reasons.isEmpty(), reasons, questions,
                new TicketIntelligenceView.Runtime("service-desk@0.3.0", "ticket-assistant@0.1.0"));
    }

    private Triage triage(Ticket ticket) {
        try {
            var result = intelligence.suggest(
                    ticket.workspace(),
                    new TriageInput("service-desk", "0.3.0", ticket.title(), ticket.description()));
            return projection.project(result, ticket);
        } catch (IntelligenceFailure failure) {
            if (failure.status() == 503 || failure.status() == 504) {
                return TicketTriageProjection.unavailable();
            }

            throw failure;
        }
    }

    private Guidance guidance(Ticket ticket, Triage triage, TicketAssessmentRequest request) {
        if (!request.includeGuidance()) {
            return emptyGuidance("NOT_REQUESTED", "NOT_REQUESTED");
        }

        if ("DEGRADED".equals(triage.status())) {
            return emptyGuidance("UNAVAILABLE", "TRIAGE_UNAVAILABLE");
        }

        if (!assistance.availableFor(ticket.workspace())) {
            return emptyGuidance("UNAVAILABLE", "ASSISTANCE_DISABLED");
        }

        String question = request.question() == null
                ? "Cần kiểm tra gì cho yêu cầu hỗ trợ: " + ticket.title() + "?"
                : request.question();

        try {
            var result = assistance.assist(ticket.workspace(), ticket.id(), question);

            if ("SUCCESS".equals(result.status()) && "GROUNDED".equals(result.reasonCode())
                    && result.answer() != null && !result.answer().isBlank()
                    && !result.citations().isEmpty()) {
                return new Guidance("SUPPORTED", result.answer(), result.citations(), result.reasonCode());
            }

            if ("INSUFFICIENT_EVIDENCE".equals(result.status()) && result.answer() == null
                    && result.citations().isEmpty()) {
                return emptyGuidance("INSUFFICIENT_EVIDENCE", "INSUFFICIENT_EVIDENCE");
            }

            throw IntelligenceFailure.invalid();
        } catch (IntelligenceFailure failure) {
            if (failure.status() == 503 || failure.status() == 504) {
                return emptyGuidance("UNAVAILABLE", failure.code());
            }

            throw failure;
        }
    }

    private static Guidance emptyGuidance(String status, String reason) {
        return new Guidance(status, null, List.of(), reason);
    }

    private static void requireIdentity(Ticket ticket, String workspace, UUID id) {
        if (!workspace.equals(ticket.workspace()) || !id.equals(ticket.id())) {
            throw CapabilityException.conflict("Ticket identity changed");
        }
    }
}
