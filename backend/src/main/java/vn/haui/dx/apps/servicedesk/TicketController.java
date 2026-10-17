package vn.haui.dx.apps.servicedesk;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.common.PageRequest;
import vn.haui.dx.core.workflow.api.ProcessHistory;
import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/apps/servicedesk/v1/workspaces/{workspace}/tickets")
public class TicketController {
    private final TicketService tickets;

    public TicketController(TicketService tickets) { this.tickets = tickets; }

    @PostMapping
    @ResponseStatus(CREATED)
    public Ticket create(@PathVariable String workspace, @Valid @RequestBody NewTicket input) {
        return tickets.create(workspace, input);
    }

    @GetMapping("/{id}")
    public Ticket get(@PathVariable String workspace, @PathVariable UUID id) {
        return tickets.get(workspace, id);
    }

    @GetMapping
    public Page<Ticket> search(@PathVariable String workspace, @RequestParam(required = false) String q,
                              @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return tickets.search(workspace, q, PageRequest.of(page, size));
    }

    @GetMapping("/{id}/progress")
    public ProcessHistory progress(@PathVariable String workspace, @PathVariable UUID id) {
        return tickets.progress(workspace, id);
    }
}
