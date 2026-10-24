package vn.haui.dx.hpdi.process.internal;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.haui.dx.core.workflow.api.*;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.process.api.*;
import vn.haui.dx.hpdi.process.api.ProcessHandle;
import vn.haui.dx.hpdi.process.api.ProcessSummary;

/** Composes request correlation and a universal inbox without exposing engine/Core models. */
@Service
public class CoreProcessCapabilities implements ProcessCapability, WorkInbox {
    private final WorkflowService workflow;
    public CoreProcessCapabilities(WorkflowService workflow) { this.workflow = workflow; }
    public ProcessHandle start(String workspace, String key, String businessKey, Map<String,Object> variables) {
        var ref = workflow.start(workspace, key, businessKey, variables);
        return new ProcessHandle(ref.processInstanceId(), ref.processKey(), ref.businessKey(), ref.workspace());
    }
    public ProcessTimeline timeline(String workspace, String id) {
        var h = workflow.history(workspace, id);
        return new ProcessTimeline(h.id(), h.processKey(), h.businessKey(), h.workspace(), h.initiator(),
            h.startedAt(), h.endedAt(), h.currentTasks().stream().map(CoreProcessCapabilities::item).toList(),
            h.steps().stream().map(s -> new ProcessTimeline.Step(s.id(), s.key(), s.name(), s.assignee(),
                s.outcome(), s.comment(), s.startedAt(), s.claimedAt(), s.endedAt())).toList());
    }
    public Page<ProcessSummary> startedByMe(String workspace, PageRequest page) {
        var result = workflow.startedByMe(workspace, new vn.haui.dx.core.common.PageRequest(page.page(), page.size()));
        return new Page<>(result.items().stream().map(p -> new ProcessSummary(p.id(), p.processKey(), p.businessKey(),
            p.startedAt(), p.endedAt(), p.activeTaskKeys())).toList(), result.page(), result.size(), result.total());
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
