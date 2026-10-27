package vn.haui.dx.apps.servicedesk;

import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.common.api.*;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.process.api.*;

@RestController
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class AssignmentSuggestionController {
    private final JdbcTemplate jdbc;
    private final TicketRepository tickets;
    private final AgentDirectory directory;
    private final AgentDuty duty;
    private final HumanContext humans;
    private final PermissionGuard permissions;
    private final WorkExecution execution;
    private final ProcessCapability processes;
    private final List<WorkProjection> projections;
    private final ZoneId zone;

    public AssignmentSuggestionController(JdbcTemplate jdbc, TicketRepository tickets, AgentDirectory directory,
            AgentDuty duty, HumanContext humans, PermissionGuard permissions, WorkExecution execution,
            ProcessCapability processes, List<WorkProjection> projections,
            @Value("${dx.assignment.business-zone:Asia/Ho_Chi_Minh}") String zone) {
        this.jdbc=jdbc; this.tickets=tickets; this.directory=directory; this.duty=duty; this.humans=humans;
        this.permissions=permissions; this.execution=execution; this.processes=processes;
        this.projections=List.copyOf(projections); this.zone=ZoneId.of(zone);
    }

    public record Candidate(String subject, String displayName, boolean onDuty, long loadMinutes,
                            long assignedToday, Integer shiftRemainingMinutes, boolean recommended, String excludedReason) {}
    public record Suggestion(String taskId, String group, int estimateMinutes, String recommended,
                             List<Candidate> candidates, String reason) {}

    @GetMapping("/api/hpdi/v1/workspaces/{workspace}/tasks/{id}/assignment-suggestion")
    public Suggestion task(@PathVariable String workspace, @PathVariable String id) {
        authorize(workspace);
        var item=execution.get(workspace,id);
        if (!ServiceDeskProcess.REVIEWED_KEY.equals(item.task().processKey()) || !"handle".equals(item.task().key()))
            throw CapabilityException.conflict("Reviewed Service Desk handle task required");
        var ticket=tickets.find(workspace,UUID.fromString(item.task().businessKey()),null)
            .orElseThrow(() -> CapabilityException.notFound("Ticket not found"));
        var review=jdbc.queryForMap("select category,estimate_minutes from servicedesk_review where ticket_id=?",ticket.id());
        return suggest(workspace,id,ticket,TicketReview.Category.valueOf((String)review.get("category")),
            ((Number)review.get("estimate_minutes")).intValue());
    }

    @GetMapping("/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/assignment-suggestion")
    public Suggestion preview(@PathVariable String workspace, @PathVariable UUID id,
            @RequestParam TicketReview.Category category, @RequestParam TicketReview.Priority priority,
            @RequestParam(required=false) Integer estimateMinutes) {
        authorize(workspace);
        if (estimateMinutes!=null && (estimateMinutes<1 || estimateMinutes>10080))
            throw CapabilityException.validation("estimateMinutes must be between 1 and 10080");
        var ticket=tickets.find(workspace,id,null).orElseThrow(() -> CapabilityException.notFound("Ticket not found"));
        if (!ServiceDeskProcess.REVIEWED_KEY.equals(ticket.process().processKey()))
            throw CapabilityException.conflict("Reviewed Service Desk ticket required");
        if (humans.currentActor(workspace).subject().equals(ticket.creator()))
            throw CapabilityException.forbidden("Independent reviewer required");
        var review=processes.timeline(workspace,ticket.process().processInstanceId()).currentTasks().stream()
            .filter(task -> "review".equals(task.key())).findFirst()
            .orElseThrow(() -> CapabilityException.conflict("Ticket is not awaiting review"));
        return suggest(workspace,review.id(),ticket,category,
            estimateMinutes==null ? TicketReview.defaultMinutes(category,priority) : estimateMinutes);
    }

    private void authorize(String workspace) {
        if (!humans.currentActor(workspace).hasRole("coordinator")) throw CapabilityException.forbidden("Coordinator required");
        permissions.require(workspace,Permissions.TASK_ASSIGN);
    }

    private Suggestion suggest(String workspace, String taskId, Ticket ticket, TicketReview.Category category, int estimate) {
        String group=TicketAssignmentEligibility.supportGroup(category);
        var agents=directory.currentAgents(workspace,group);
        var statuses=new HashMap<String,Boolean>();
        duty.list(workspace).forEach(status -> statuses.put(status.subject(),status.onDuty()));
        var loads=loads(workspace,taskId);
        var today=LocalDate.now(zone);
        var counts=new HashMap<String,Long>();
        jdbc.query("select subject,count(*) as total from servicedesk_assignment_event where workspace=?"
            + " and assigned_at>=? and assigned_at<? group by subject",
            (org.springframework.jdbc.core.RowCallbackHandler) row -> counts.put(row.getString("subject"),row.getLong("total")),
            workspace,Timestamp.from(today.atStartOfDay(zone).toInstant()),Timestamp.from(today.plusDays(1).atStartOfDay(zone).toInstant()));
        var cursors=group==null ? List.<String>of() : jdbc.query("select last_subject from servicedesk_assignment_cursor"
            + " where workspace=? and support_group=?",(row,index) -> row.getString(1),workspace,group);
        var candidates=agents.stream().map(agent -> {
            boolean onDuty=statuses.getOrDefault(agent.subject(),false);
            String excluded=agent.subject().equals(ticket.creator()) ? "IS_REQUESTER" : onDuty ? null : "OFF_DUTY";
            return new Candidate(agent.subject(),agent.fullName()==null ? agent.username() : agent.fullName(),onDuty,
                loads.getOrDefault(agent.subject(),0L),counts.getOrDefault(agent.subject(),0L),null,false,excluded);
        }).toList();
        var ranked=rank(candidates,cursors.isEmpty() ? null : cursors.get(0));
        String selected=group==null || ranked.isEmpty() ? null : ranked.get(0).subject();
        var result=new ArrayList<Candidate>();
        ranked.forEach(candidate -> result.add(mark(candidate,selected)));
        candidates.stream().filter(candidate -> candidate.excludedReason()!=null).sorted(Comparator.comparing(Candidate::subject)).forEach(result::add);
        String reason=group==null ? "OTHER requires manual coordinator selection" : selected==null ? "No eligible on-duty agent in support group"
            : "Minimum-load band (<15 minutes from minimum; considered task excluded), then fewest assignments today, then committed-assignment round-robin; selected "
                + selected + " with " + ranked.get(0).loadMinutes() + " load minutes and " + ranked.get(0).assignedToday() + " assignments today";
        return new Suggestion(taskId,group==null ? "manual" : group,estimate,selected,List.copyOf(result),reason);
    }

    private Map<String,Long> loads(String workspace, String excludedTaskId) {
        var loads=new HashMap<String,Long>();
        for (int page=0; ; page++) {
            var batch=execution.workspaceQueue(workspace,new PageRequest(page,100));
            if (batch.total()>10000) throw CapabilityException.conflict("Suggestion workload limit exceeded (10000 tasks)");
            var grouped=batch.items().stream().filter(item -> !excludedTaskId.equals(item.task().id()))
                .collect(java.util.stream.Collectors.groupingBy(item -> item.task().processKey()));
            grouped.forEach((key,items) -> {
                var provider=projections.stream().filter(projection -> projection.processKey().equals(key)).findFirst()
                    .orElseThrow(() -> CapabilityException.conflict("Work projection required for " + key));
                provider.projectBatch(workspace,items).forEach(item -> loads.merge(item.task().assignee(),item.remainingMinutes(),Math::addExact));
            });
            if ((long)(page+1)*100>=batch.total()) return loads;
        }
    }

    static List<Candidate> rank(List<Candidate> candidates, String cursor) {
        var remaining=new ArrayList<>(candidates.stream().filter(candidate -> candidate.excludedReason()==null)
            .sorted(Comparator.comparingLong(Candidate::loadMinutes).thenComparing(Candidate::subject)).toList());
        var result=new ArrayList<Candidate>();
        while (!remaining.isEmpty()) {
            long minimum=remaining.get(0).loadMinutes();
            var band=remaining.stream().filter(candidate -> candidate.loadMinutes()-minimum<15)
                .sorted(Comparator.comparingLong(Candidate::assignedToday)
                    .thenComparingInt(candidate -> cursor==null || candidate.subject().compareTo(cursor)>0 ? 0 : 1)
                    .thenComparing(Candidate::subject)).toList();
            result.addAll(band);
            remaining.removeAll(band);
        }
        return List.copyOf(result);
    }

    private static Candidate mark(Candidate candidate, String selected) {
        return new Candidate(candidate.subject(),candidate.displayName(),candidate.onDuty(),candidate.loadMinutes(),
            candidate.assignedToday(),null,candidate.subject().equals(selected),candidate.excludedReason());
    }
}
