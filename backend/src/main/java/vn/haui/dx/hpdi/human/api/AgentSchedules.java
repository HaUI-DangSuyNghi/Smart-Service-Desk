package vn.haui.dx.hpdi.human.api;

import java.time.Instant;
import java.util.List;

public interface AgentSchedules {
    record Shift(Instant startsAt, Instant endsAt, String timezone) {}
    record Change(Long version, Boolean enabled, List<Shift> shifts) {}
    record Schedule(String subject, long version, boolean enabled, List<Shift> shifts) {}
    record Effective(boolean manualDuty, boolean scheduleEnabled, boolean scheduledDuty, boolean onDuty,
                     Instant shiftStart, Instant shiftEnd, Integer shiftRemainingMinutes,
                     long scheduleVersion, String reason) {}
    Schedule get(String workspace, String subject);
    List<Schedule> list(String workspace);
    Schedule replace(String workspace, String subject, Change change);
    Effective effective(String workspace, String subject);
}
