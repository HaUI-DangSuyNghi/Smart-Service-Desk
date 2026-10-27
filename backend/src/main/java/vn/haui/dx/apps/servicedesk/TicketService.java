package vn.haui.dx.apps.servicedesk;

import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.hpdi.human.api.ActivityJournal;
import vn.haui.dx.hpdi.human.api.BusinessActivity;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.human.api.Actor;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.process.api.ProcessCapability;

@Service
public class TicketService {
    private final TicketRepository repository;
    private final HumanContext access;
    private final ProcessCapability workflow;
    private final ActivityJournal audit;
    private final Validator validator;
    private final TicketStatisticsService statistics;
    private final vn.haui.dx.apps.servicedesk.intelligence.BackgroundTicketAssessment assessments;

    public TicketService(TicketRepository repository, HumanContext access, ProcessCapability workflow,
                         ActivityJournal audit, Validator validator, TicketStatisticsService statistics,
                         vn.haui.dx.apps.servicedesk.intelligence.BackgroundTicketAssessment assessments) {
        this.repository = repository;
        this.access = access;
        this.workflow = workflow;
        this.audit = audit;
        this.validator = validator;
        this.statistics = statistics;
        this.assessments = assessments;
    }

    @Transactional
    public Ticket create(String workspace, NewTicket input) {
        var user = member(workspace);
        if (input == null || !validator.validate(input).isEmpty()) throw CapabilityException.validation("Invalid ticket");
        text(input.title(), 200, false);
        text(input.description(), 4000, true);
        UUID id = UUID.randomUUID();
        var process = workflow.start(workspace, ServiceDeskProcess.REVIEWED_KEY, id.toString(), Map.of());
        var ticket = repository.insert(id, workspace, user.subject(), input, process.processInstanceId());
        assessments.enqueue(ticket);
        audit.record(workspace, new BusinessActivity("servicedesk.ticket.create", "servicedesk.ticket", id.toString(),
            Map.of("processInstanceId", process.processInstanceId())));
        return ticket;
    }

    @Transactional(readOnly = true)
    public Ticket get(String workspace, UUID id) {
        var user = member(workspace);
        if (id == null) throw CapabilityException.validation("Ticket id is required");
        return repository.find(workspace, id, reader(user, workspace))
            .orElseThrow(() -> CapabilityException.notFound("Ticket not found"));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<Ticket> search(String workspace, String query, Instant from, Instant to, PageRequest page) {
        var user = member(workspace);
        if (page == null) throw CapabilityException.validation("Page is required");
        filters(query, from, to);
        return repository.search(workspace, reader(user, workspace), query, from, to, page);
    }

    @Transactional(readOnly = true)
    public TicketStatistics statistics(String workspace, String query, Instant from, Instant to) {
        var user = member(workspace);
        filters(query, from, to);
        return statistics.statistics(workspace, reader(user, workspace), query, from, to);
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Page<Ticket> awaitingReview(String workspace, String query, Instant from, Instant to, PageRequest page, String sort) {
        if (!member(workspace).hasRole("coordinator")) throw CapabilityException.forbidden("Coordinator required");
        filters(query,from,to);
        if (sort!=null && !java.util.Set.of("createdAt","priority").contains(sort)) throw CapabilityException.validation("Invalid sort");
        return repository.awaitingReview(workspace,query,from,to,page,"priority".equals(sort));
    }

    private static void filters(String query, Instant from, Instant to) {
        if (query != null && (query.length() > 200 || query.codePoints().anyMatch(Character::isISOControl)))
            throw CapabilityException.validation("Invalid ticket query");
        for (Instant bound : new Instant[] {from, to}) {
            if (bound != null && (bound.getNano() % 1000 != 0
                || bound.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                || !bound.isBefore(Instant.parse("+10000-01-01T00:00:00Z"))))
                throw CapabilityException.validation("Time filter requires years 0001-9999 and microsecond precision");
        }
        if (from != null && to != null && !from.isBefore(to))
            throw CapabilityException.validation("from must precede to");
    }

    @Transactional(readOnly = true)
    public TicketProgress progress(String workspace, UUID id) {
        return TicketProgress.from(workflow.timeline(workspace, get(workspace, id).process().processInstanceId()));
    }

    private Actor member(String workspace) {
        var user = access.currentActor(workspace);
        if (workspace == null || !user.hasRole("requester", "agent", "coordinator"))
            throw CapabilityException.forbidden("Service Desk workspace role required");
        return user;
    }

    private static String reader(Actor user, String workspace) {
        return user.hasRole("agent", "coordinator") ? null : user.subject();
    }

    private static void text(String value, int limit, boolean multiline) {
        if (value == null || value.isBlank() || value.length() > limit || value.codePoints().anyMatch(code ->
            Character.isISOControl(code) && !(multiline && (code == '\n' || code == '\r' || code == '\t'))))
            throw CapabilityException.validation("Invalid ticket text");
    }
}
