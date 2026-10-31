package vn.haui.dx.apps.roomrequest;

import java.util.List;
import vn.haui.dx.hpdi.process.api.ProcessTimeline;

public record RoomProgress(String id, boolean ended, List<WorkStep> currentTasks, boolean cancelled) {
    public RoomProgress { currentTasks = List.copyOf(currentTasks); }
    public record WorkStep(String id, String key, String name, String assignee) {}
    static RoomProgress from(ProcessTimeline timeline) {
        return new RoomProgress(timeline.id(), timeline.ended(), timeline.currentTasks().stream()
            .map(task -> new WorkStep(task.id(), task.key(), task.name(), task.assignee())).toList(),timeline.cancelled());
    }
}
