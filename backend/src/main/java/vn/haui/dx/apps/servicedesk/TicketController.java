package vn.haui.dx.apps.servicedesk;

import java.util.UUID;
import java.time.Instant;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/apps/servicedesk/v1/workspaces/{workspace}/tickets")
public class TicketController {
    private final TicketService tickets;
    private final TicketReviewService reviews;

    public TicketController(TicketService tickets, TicketReviewService reviews) { this.tickets = tickets; this.reviews = reviews; }

    @PostMapping
    @ResponseStatus(CREATED)
    @org.springframework.transaction.annotation.Transactional
    public TicketReview.View create(@PathVariable String workspace, @Valid @RequestBody NewTicket input) {
        return reviews.view(workspace, tickets.create(workspace, input));
    }

    @GetMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object get(@PathVariable String workspace, @PathVariable UUID id) {
        var ticket=tickets.get(workspace,id);
        return ServiceDeskProcess.KEY.equals(ticket.process().processKey()) ? ticket : reviews.view(workspace,ticket);
    }

    @PostMapping("/{id}/review")
    public TicketReview.View review(@PathVariable String workspace, @PathVariable UUID id,
                                  @Valid @RequestBody TicketReview.Decision decision) {
        return reviews.review(workspace, id, decision);
    }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<?> search(@PathVariable String workspace, @RequestParam(required = false) String q,
                              @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
                              @RequestParam(required = false) String state, @RequestParam(required = false) String sort,
                              @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        if (state != null) {
            if (!"AWAITING_REVIEW".equals(state)) throw vn.haui.dx.hpdi.common.api.CapabilityException.validation("Only AWAITING_REVIEW filtering is implemented");
            var result=tickets.awaitingReview(workspace,q,from,to,PageRequest.of(page,size),sort);
            return new Page<>(result.items().stream().map(ticket -> reviews.view(workspace,ticket)).toList(),result.page(),result.size(),result.total());
        }
        if (sort!=null) throw vn.haui.dx.hpdi.common.api.CapabilityException.validation("sort requires AWAITING_REVIEW state");
        return tickets.search(workspace, q, from, to, PageRequest.of(page, size));
    }

    @GetMapping("/statistics")
    public TicketStatistics statistics(@PathVariable String workspace, @RequestParam(required = false) String q,
                                       @RequestParam(required = false) Instant from,
                                       @RequestParam(required = false) Instant to) {
        return tickets.statistics(workspace, q, from, to);
    }

    @GetMapping("/{id}/progress")
    public TicketProgress progress(@PathVariable String workspace, @PathVariable UUID id) {
        return tickets.progress(workspace, id);
    }
}
