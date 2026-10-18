package vn.haui.dx.apps.servicedesk;

import java.time.Instant;
import java.util.List;
import vn.haui.dx.hpdi.process.api.ProcessTimeline;

/** Service Desk owns its REST representation, with existing wire fields preserved. */
public record TicketProgress(String id, String processKey, String businessKey, String workspace, String initiator,
        Instant startedAt, Instant endedAt, List<Task> currentTasks, List<Step> steps) {
    public TicketProgress { currentTasks = List.copyOf(currentTasks); steps = List.copyOf(steps); }
    @com.fasterxml.jackson.annotation.JsonProperty("ended") public boolean ended() { return endedAt != null; }
    @com.fasterxml.jackson.annotation.JsonProperty("tasks") public List<Step> tasks() { return steps; }
    public record Task(String id, String processInstanceId, String key, String name, List<String> candidateGroups,
                       String assignee, Instant createdAt) {
        public Task { candidateGroups = List.copyOf(candidateGroups); }
    }
    public record Step(String id, String key, String name, String assignee, String outcome, String comment,
                       Instant startedAt, Instant claimedAt, Instant endedAt) {
        @com.fasterxml.jackson.annotation.JsonProperty("actor") public String actor() { return assignee; }
    }
    static TicketProgress from(ProcessTimeline timeline) {
        return new TicketProgress(timeline.id(), timeline.processKey(), timeline.businessKey(), timeline.workspace(),
            timeline.initiator(), timeline.startedAt(), timeline.endedAt(), timeline.currentTasks().stream().map(task ->
                new Task(task.id(), task.processInstanceId(), task.key(), task.name(), task.candidateGroups(), task.assignee(), task.createdAt())).toList(),
            timeline.steps().stream().map(step -> new Step(step.id(), step.key(), step.name(), step.assignee(),
                step.outcome(), step.comment(), step.startedAt(), step.claimedAt(), step.endedAt())).toList());
    }
}
