package vn.haui.dx.apps.servicedesk.triage;

import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceCapability;
import vn.haui.dx.hpdi.intelligence.api.TriageInput;

@Service
public class TicketTriageService {
    private final TicketService tickets;
    private final IntelligenceCapability intelligence;

    public TicketTriageService(TicketService tickets, IntelligenceCapability intelligence) {
        this.tickets = tickets;
        this.intelligence = intelligence;
    }

    public TicketTriageView triage(String workspace, UUID id) {
        var ticket = tickets.get(workspace, id);
        var suggestion = intelligence.suggest(ticket.workspace(), new TriageInput("service-desk", "0.2.0", ticket.title(), ticket.description()));
        return new TicketTriageView(suggestion.attributes());
    }
}
