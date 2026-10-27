package vn.haui.dx.apps.servicedesk;

import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import jakarta.validation.Validator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.apps.servicedesk.intelligence.BackgroundTicketAssessment;
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
    private final ApplicationEventPublisher events;
    private final BackgroundTicketAssessment assessments;
    private final org.springframework.beans.factory.ObjectProvider<vn.haui.dx.hpdi.intelligence.api.BackgroundProcessing> processing;
    private final boolean trueWait;

    public TicketService(TicketRepository repository, HumanContext access, ProcessCapability workflow,
                         ActivityJournal audit, Validator validator, TicketStatisticsService statistics,
                         ApplicationEventPublisher events, BackgroundTicketAssessment assessments,
                         org.springframework.beans.factory.ObjectProvider<vn.haui.dx.hpdi.intelligence.api.BackgroundProcessing> processing,
                         @org.springframework.beans.factory.annotation.Value("${serviceDesk.intake.true-wait:false}") boolean trueWait,
                         @org.springframework.beans.factory.annotation.Value("${serviceDesk.intake.new-tickets:false}") boolean intakeEnabled) {
        if (trueWait && !intakeEnabled) throw new IllegalArgumentException("True intake wait requires new-ticket admission");
        this.repository = repository;
        this.access = access;
        this.workflow = workflow;
        this.audit = audit;
        this.validator = validator;
        this.statistics = statistics;
        this.events = events;
        this.assessments = assessments;
        this.processing = processing;
        this.trueWait = trueWait;
    }

    @Transactional
    public Ticket create(String workspace, NewTicket input) {
        var user = member(workspace);
        if (input == null || !validator.validate(input).isEmpty()) throw CapabilityException.validation("Invalid ticket");
        text(input.title(), 200, false);
        text(input.description(), 4000, true);
        UUID id = UUID.randomUUID();
        var process = workflow.start(workspace, trueWait ? ServiceDeskProcess.INTAKE_KEY : ServiceDeskProcess.REVIEWED_KEY, id.toString(), Map.of());
        var ticket = repository.insert(id, workspace, user.subject(), input, process.processInstanceId(), process.processKey());
        assessments.enqueue(ticket);
        audit.record(workspace, new BusinessActivity("servicedesk.ticket.create", "servicedesk.ticket", id.toString(),
            Map.of("processInstanceId", process.processInstanceId())));
        events.publishEvent(new TicketContextChanged(workspace, id, 0));
        return ticket;
    }

    @Transactional(readOnly = true)
    public boolean requiresFinalAdmission(String workspace, UUID id) {
        get(workspace, id);
        return repository.hasIntake(workspace, id);
    }

    @Transactional(readOnly = true)
    public Ticket getForIntelligence(String workspace, UUID id) {
        var actor = access.currentActor(workspace);
        if (actor == null || !workspace.equals(actor.workspace()) || !actor.hasRole("assessment-worker")) {
            return get(workspace, id);
        }
        var ticket = repository.find(workspace, id, null)
                .orElseThrow(() -> CapabilityException.notFound("Ticket not found"));
        processing.getObject().authorize(workspace, ticket.creator());
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
