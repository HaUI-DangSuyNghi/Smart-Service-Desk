package vn.haui.dx.apps.servicedesk;

import java.util.UUID;
import java.time.Instant;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.common.PageRequest;
import vn.haui.dx.hpdi.process.api.ProcessTimeline;
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
                              @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
                              @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return tickets.search(workspace, q, from, to, PageRequest.of(page, size));
    }

    @GetMapping("/statistics")
    public TicketStatistics statistics(@PathVariable String workspace, @RequestParam(required = false) String q,
                                       @RequestParam(required = false) Instant from,
                                       @RequestParam(required = false) Instant to) {
        return tickets.statistics(workspace, q, from, to);
    }

    @GetMapping("/{id}/progress")
    public ProcessTimeline progress(@PathVariable String workspace, @PathVariable UUID id) {
        return tickets.progress(workspace, id);
    }
}
