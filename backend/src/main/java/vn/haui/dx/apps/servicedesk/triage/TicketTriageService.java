package vn.haui.dx.apps.servicedesk.triage;

import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import vn.haui.dx.apps.servicedesk.TicketService;

@Service
public class TicketTriageService {
    private final TicketService tickets;
    private final IntelligenceTriageClient intelligence;
    private final TriageValidator validator;

    public TicketTriageService(TicketService tickets, IntelligenceTriageClient intelligence, TriageValidator validator) {
        this.tickets = tickets;
        this.intelligence = intelligence;
        this.validator = validator;
    }

    public JsonNode triage(String workspace, UUID id) {
        var ticket = tickets.get(workspace, id);
        var request = new TriageRequest("0.1.0", UUID.randomUUID().toString(), "service-desk",
            new TriageRequest.Context(ticket.workspace()), new TriageRequest.Input(ticket.title(), ticket.description()));
        validator.request(request);
        var response = intelligence.triage(request);
        validator.response(request, response);
        return response;
    }
}
