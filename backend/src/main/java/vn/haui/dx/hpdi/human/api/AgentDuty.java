package vn.haui.dx.hpdi.human.api;

import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public interface AgentDuty {
    record Change(@NotNull Boolean onDuty, @Size(max=500) String note) {}
    record Status(String subject, boolean onDuty, String note, Instant updatedAt, boolean effectiveDuty) {
        public Status(String subject, boolean onDuty, String note, Instant updatedAt) {
            this(subject,onDuty,note,updatedAt,onDuty);
        }
    }
    List<Status> list(String workspace);
    Status set(String workspace, String subject, Change change);
    boolean onDuty(String workspace, String subject);
    default boolean manualDuty(String workspace, String subject) { return onDuty(workspace,subject); }
    default Integer shiftRemainingMinutes(String workspace, String subject) { return null; }
}
