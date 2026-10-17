package vn.haui.dx.apps.servicedesk;

import java.util.Map;
import java.util.UUID;
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
import vn.haui.dx.core.workflow.api.ProcessHistory;
import vn.haui.dx.core.workflow.api.WorkflowService;
import static vn.haui.dx.core.identity.api.WorkspaceRole.*;

@Service
public class TicketService {
    private final TicketRepository repository;
    private final WorkspaceAccess access;
    private final WorkflowService workflow;
    private final AuditService audit;
    private final Validator validator;

    public TicketService(TicketRepository repository, WorkspaceAccess access, WorkflowService workflow,
                         AuditService audit, Validator validator) {
        this.repository = repository;
        this.access = access;
        this.workflow = workflow;
        this.audit = audit;
        this.validator = validator;
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
    public Page<Ticket> search(String workspace, String query, PageRequest page) {
        var user = member(workspace);
        if (page == null) throw CoreException.validation("Page is required");
        if (query != null && (query.length() > 200 || query.codePoints().anyMatch(Character::isISOControl)))
            throw CoreException.validation("Invalid ticket query");
        return repository.search(workspace, reader(user, workspace), query, page);
    }

    @Transactional(readOnly = true)
    public ProcessHistory progress(String workspace, UUID id) {
        return workflow.history(workspace, get(workspace, id).process().processInstanceId());
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
