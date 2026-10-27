package vn.haui.dx.hpdi.process.internal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.haui.dx.core.workflow.api.*;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.process.api.*;
import vn.haui.dx.hpdi.process.api.ProcessHandle;
import vn.haui.dx.hpdi.process.api.ProcessStatistics;
import vn.haui.dx.hpdi.process.api.ProcessSummary;

/** Composes request correlation and a universal inbox without exposing engine/Core models. */
@Service
public class CoreProcessCapabilities implements ProcessCapability, WorkInbox, WorkExecution {
    private final WorkflowService workflow;
    public CoreProcessCapabilities(WorkflowService workflow) { this.workflow = workflow; }
    public WorkExecution.Item get(String workspace, String taskId) { return execution(workflow.execution(workspace, taskId)); }
    public Page<WorkExecution.Item> workspaceQueue(String workspace, PageRequest page) {
        var result = workflow.workspaceQueue(workspace, corePage(page));
        return new Page<>(result.items().stream().map(CoreProcessCapabilities::execution).toList(),
            result.page(), result.size(), result.total());
    }
    public WorkExecution.Item execute(String workspace, String taskId, WorkExecution.Action action, String reason, String note) {
        if (action == null) throw vn.haui.dx.hpdi.common.api.CapabilityException.validation("action is required");
        if (action == WorkExecution.Action.PAUSE) {
            if (!java.util.Set.of("WAITING_REQUESTER", "WAITING_PARTS", "WAITING_EXTERNAL", "OTHER").contains(reason == null ? "" : reason))
                throw vn.haui.dx.hpdi.common.api.CapabilityException.validation("Invalid pause reason");
            if ("OTHER".equals(reason) && (note == null || note.isBlank()))
                throw vn.haui.dx.hpdi.common.api.CapabilityException.validation("OTHER requires a note");
        }
        return execution(workflow.execute(workspace, taskId, TaskExecution.Action.valueOf(action.name()), reason, note));
    }
    public Page<WorkExecution.Item> queue(String workspace, String assignee, PageRequest page) {
        if (page == null) throw vn.haui.dx.hpdi.common.api.CapabilityException.validation("Page is required");
        var result = workflow.queue(workspace, assignee, corePage(page));
        return new Page<>(result.items().stream().map(CoreProcessCapabilities::execution).toList(),
            result.page(), result.size(), result.total());
    }
    private static WorkExecution.Item execution(TaskExecution execution) {
        return new WorkExecution.Item(item(execution.task()), execution.state().name(), execution.priority(),
            execution.dueAt(), execution.workedMillis(), execution.startedAt(), execution.pendingReason(), execution.pendingNote());
    }
    public ProcessHandle start(String workspace, String key, String businessKey, Map<String,Object> variables) {
        var ref = workflow.start(workspace, key, businessKey, variables);
        return new ProcessHandle(ref.processInstanceId(), ref.processKey(), ref.businessKey(), ref.workspace());
    }
    public ProcessTimeline timeline(String workspace, String id) {
        var h = workflow.history(workspace, id);
        return new ProcessTimeline(h.id(), h.processKey(), h.businessKey(), h.workspace(), h.initiator(),
            h.startedAt(), h.endedAt(), h.currentTasks().stream().map(CoreProcessCapabilities::item).toList(),
            h.steps().stream().map(s -> new ProcessTimeline.Step(s.id(), s.key(), s.name(), s.assignee(),
                s.outcome(), s.comment(), s.startedAt(), s.claimedAt(), s.endedAt())).toList(), h.variables());
    }
    public Page<ProcessSummary> startedByMe(String workspace, PageRequest page) {
        return summaries(workflow.startedByMe(workspace, corePage(page)));
    }
    public Page<ProcessSummary> readable(String workspace, PageRequest page) {
        return summaries(workflow.readable(workspace, corePage(page)));
    }
    public ProcessStatistics statistics(String workspace, Instant from, Instant to, ZoneId zone) {
        var s = workflow.statistics(workspace, from, to, zone);
        return new ProcessStatistics(s.from(), s.to(),
            s.processes().stream().map(p -> new ProcessStatistics.ProcessCount(p.processKey(), p.started(), p.running(),
                p.completed(), p.averageCompletionSeconds(), p.waitingClaim(), p.inProgress())).toList(),
            s.daily().stream().map(d -> new ProcessStatistics.DailyCount(d.date(), d.started())).toList());
    }
    public WorkItem assign(String workspace, String taskId, String assignee) {
        return item(workflow.assign(workspace, taskId, assignee));
    }
    public void setPriority(String workspace, String taskId, int priority) { workflow.setPriority(workspace, taskId, priority); }
    private static Page<ProcessSummary> summaries(vn.haui.dx.core.common.Page<vn.haui.dx.core.workflow.api.ProcessSummary> result) {
        return new Page<>(result.items().stream().map(p -> new ProcessSummary(p.id(), p.processKey(), p.businessKey(),
            p.initiator(), p.startedAt(), p.endedAt(), p.activeTaskKeys())).toList(), result.page(), result.size(), result.total());
    }
    private static vn.haui.dx.core.common.PageRequest corePage(PageRequest page) {
        return new vn.haui.dx.core.common.PageRequest(page.page(), page.size());
    }
    public Page<WorkItem> list(String workspace, Scope scope, PageRequest page) {
        var result = workflow.inbox(workspace, new InboxFilter(InboxFilter.Scope.valueOf(scope.name())),
            new vn.haui.dx.core.common.PageRequest(page.page(), page.size()));
        return new Page<>(result.items().stream().map(CoreProcessCapabilities::item).toList(),
            result.page(), result.size(), result.total());
    }
    public WorkItem claim(String workspace, String taskId) { return item(workflow.claim(workspace, taskId)); }
    public void complete(String workspace, String taskId, String key, String outcome, String comment) {
        workflow.complete(workspace, taskId, key, outcome, comment);
    }
    private static WorkItem item(TaskView t) {
        return new WorkItem(t.id(), t.processInstanceId(), t.key(), t.name(), t.candidateGroups(), t.assignee(),
            t.createdAt(), t.processKey(), t.businessKey());
    }
}
