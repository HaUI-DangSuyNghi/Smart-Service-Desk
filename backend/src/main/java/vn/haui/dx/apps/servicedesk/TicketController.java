package vn.haui.dx.apps.servicedesk;

import java.util.List;
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
    private final TicketCorrectionService corrections;

    public TicketController(TicketService tickets, TicketReviewService reviews, TicketCorrectionService corrections) {
        this.tickets = tickets; this.reviews = reviews; this.corrections = corrections;
    }

    @PatchMapping("/{id}/classification")
    public TicketReview.View correctClassification(@PathVariable String workspace, @PathVariable UUID id,
            @RequestBody com.fasterxml.jackson.databind.JsonNode input) {
        return corrections.correct(workspace, id, TicketCorrection.parse(input));
    }

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
                              @RequestParam(required = false) List<String> state, @RequestParam(required = false) List<String> priority,
                              @RequestParam(required = false) String category, @RequestParam(required = false) String assignee,
                              @RequestParam(required = false) Boolean overdue, @RequestParam(required = false) String sort,
                              @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        boolean extraFilters = priority != null || category != null || assignee != null || overdue != null;
        // Hàng chờ duyệt của điều phối viên giữ đường cũ: lọc theo cờ awaiting_review, cũ nhất trước
        if (List.of("AWAITING_REVIEW").equals(state) && !extraFilters) {
            var result=tickets.awaitingReview(workspace,q,from,to,PageRequest.of(page,size),sort);
            return new Page<>(result.items().stream().map(ticket -> reviews.view(workspace,ticket)).toList(),result.page(),result.size(),result.total());
        }
        var result = state == null && !extraFilters && sort == null
            ? tickets.search(workspace, q, from, to, PageRequest.of(page, size))
            : tickets.filter(workspace, q, from, to, state, priority, category, assignee, overdue, sort, PageRequest.of(page, size));
        return new Page<>(result.items().stream().map(ticket -> view(workspace, ticket)).toList(), result.page(), result.size(), result.total());
    }

    // Ticket quy trình cũ không có trạng thái R6, trả nguyên như GET /{id}
    private Object view(String workspace, Ticket ticket) {
        return ServiceDeskProcess.KEY.equals(ticket.process().processKey()) ? ticket : reviews.view(workspace, ticket);
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
