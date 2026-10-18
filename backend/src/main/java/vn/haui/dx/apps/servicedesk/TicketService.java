package vn.haui.dx.apps.servicedesk;

import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.common.PageRequest;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import vn.haui.dx.hpdi.process.api.ProcessCapability;
import static vn.haui.dx.core.identity.api.WorkspaceRole.*;

@Service
public class TicketService {
    private final TicketRepository repository;
    private final WorkspaceAccess access;
    private final ProcessCapability workflow;
    private final AuditService audit;
    private final Validator validator;
    private final TicketStatisticsService statistics;

    public TicketService(TicketRepository repository, WorkspaceAccess access, ProcessCapability workflow,
                         AuditService audit, Validator validator, TicketStatisticsService statistics) {
        this.repository = repository;
        this.access = access;
        this.workflow = workflow;
        this.audit = audit;
        this.validator = validator;
        this.statistics = statistics;
    }

    @Transactional
    public Ticket create(String workspace, NewTicket input) {
        var user = member(workspace);
        if (input == null || !validator.validate(input).isEmpty()) throw CoreException.validation("Invalid ticket");
        text(input.title(), 200, false);
        text(input.description(), 4000, true);
        UUID id = UUID.randomUUID();
        var process = workflow.start(workspace, ServiceDeskProcess.KEY, id.toString(), Map.of());
        var ticket = repository.insert(id, workspace, user.sub(), input, process.processInstanceId());
        audit.record("servicedesk.ticket.create", "servicedesk.ticket", id.toString(), workspace,
            Map.of("processInstanceId", process.processInstanceId()));
        return ticket;
    }

    @Transactional(readOnly = true)
    public Ticket get(String workspace, UUID id) {
        var user = member(workspace);
        if (id == null) throw CoreException.validation("Ticket id is required");
        return repository.find(workspace, id, reader(user, workspace))
            .orElseThrow(() -> CoreException.notFound("Ticket not found"));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<Ticket> search(String workspace, String query, Instant from, Instant to, PageRequest page) {
        var user = member(workspace);
        if (page == null) throw CoreException.validation("Page is required");
        filters(query, from, to);
        return repository.search(workspace, reader(user, workspace), query, from, to, page);
    }

    @Transactional(readOnly = true)
    public TicketStatistics statistics(String workspace, String query, Instant from, Instant to) {
        var user = member(workspace);
        filters(query, from, to);
        return statistics.statistics(workspace, reader(user, workspace), query, from, to);
    }

    private static void filters(String query, Instant from, Instant to) {
        if (query != null && (query.length() > 200 || query.codePoints().anyMatch(Character::isISOControl)))
            throw CoreException.validation("Invalid ticket query");
        for (Instant bound : new Instant[] {from, to}) {
            if (bound != null && (bound.getNano() % 1000 != 0
                || bound.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                || !bound.isBefore(Instant.parse("+10000-01-01T00:00:00Z"))))
                throw CoreException.validation("Time filter requires years 0001-9999 and microsecond precision");
        }
        if (from != null && to != null && !from.isBefore(to))
            throw CoreException.validation("from must precede to");
    }

    @Transactional(readOnly = true)
    public TicketProgress progress(String workspace, UUID id) {
        return TicketProgress.from(workflow.timeline(workspace, get(workspace, id).process().processInstanceId()));
    }

    private CurrentUser member(String workspace) {
        var user = access.current();
        if (workspace == null || !user.hasRole(workspace, REQUESTER, AGENT, COORDINATOR))
            throw CoreException.forbidden("Service Desk workspace role required");
        return user;
    }

    private static String reader(CurrentUser user, String workspace) {
        return user.hasRole(workspace, AGENT, COORDINATOR) ? null : user.sub();
    }

    private static void text(String value, int limit, boolean multiline) {
        if (value == null || value.isBlank() || value.length() > limit || value.codePoints().anyMatch(code ->
            Character.isISOControl(code) && !(multiline && (code == '\n' || code == '\r' || code == '\t'))))
            throw CoreException.validation("Invalid ticket text");
    }
}
