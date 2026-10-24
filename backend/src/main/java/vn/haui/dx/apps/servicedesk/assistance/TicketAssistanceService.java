package vn.haui.dx.apps.servicedesk.assistance;

import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.intelligence.api.AssistanceInput;
import vn.haui.dx.hpdi.intelligence.api.AssistanceOutcome;
import vn.haui.dx.hpdi.intelligence.api.GroundedAssistanceCapability;

@Service
public class TicketAssistanceService {
    private final TicketService tickets;
    private final GroundedAssistanceCapability intelligence;
    private final ServiceDeskAssistanceConfiguration.Settings settings;

    public TicketAssistanceService(
            TicketService tickets, GroundedAssistanceCapability intelligence,
            ServiceDeskAssistanceConfiguration.Settings settings) {
        this.tickets = tickets;
        this.intelligence = intelligence;
        this.settings = settings;
    }

    public AssistanceOutcome assist(String workspace, UUID id, String question) {
        // Enforce ticket visibility before any AI or knowledge access.
        var ticket = tickets.get(workspace, id);

        if (!settings.enabled() || !settings.catalogs().containsKey(ticket.workspace())) {
            throw CapabilityException.notFound("Assistance is unavailable");
        }

        if (question == null || question.isBlank() || question.length() > 2000
                || question.codePoints().anyMatch(value -> Character.isISOControl(value)
                    && value != '\n' && value != '\r' && value != '\t')) {
            throw CapabilityException.validation("A question of at most 2000 characters is required");
        }

        var result = intelligence.assist(ticket.workspace(), new AssistanceInput(
            question, "service-desk", "0.3.0", ServiceDeskAssistanceConfiguration.POLICY,
            settings.catalogs().get(ticket.workspace()), ServiceDeskAssistanceConfiguration.referenceIdentity()));

        // The read is a fresh use-case call after the bounded AI call. It must
        // stay outside a surrounding repeatable-read transaction.
        tickets.get(workspace, id);
        return result;
    }
}
