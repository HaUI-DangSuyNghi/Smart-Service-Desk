package vn.haui.dx.hpdi.process;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.haui.dx.hpdi.process.api.*;
import static vn.haui.dx.hpdi.process.api.SlaPolicy.Priority.*;
import static org.junit.jupiter.api.Assertions.*;

class SlaPolicyTest {
    private final BusinessCalendar calendar = new BusinessCalendar("Asia/Ho_Chi_Minh",
        EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), List.of(
            new BusinessCalendar.Shift(LocalTime.of(7, 0), LocalTime.of(11, 30)),
            new BusinessCalendar.Shift(LocalTime.of(13, 0), LocalTime.of(17, 0))), Set.of());
    private final SlaPolicy policy = new SlaPolicy("test-v1", calendar, Map.of(
        CRITICAL, new SlaPolicy.Target(15, 120, true), HIGH, new SlaPolicy.Target(60, 480, false),
        MEDIUM, new SlaPolicy.Target(240, 1020, false), LOW, new SlaPolicy.Target(510, 2550, false)), .5, HIGH, true);
    private Instant local(String value) { return LocalDateTime.parse(value).atZone(ZoneId.of(calendar.zone())).toInstant(); }

    @Test void allPrioritiesAndBusinessDayUnits() {
        var from = local("2026-10-26T08:00");
        var expected = Map.of(CRITICAL, "2026-10-26T10:00", HIGH, "2026-10-27T07:30",
            MEDIUM, "2026-10-28T08:00", LOW, "2026-11-02T08:00");
        for (var priority : SlaPolicy.Priority.values()) {
            var target = policy.targets().get(priority);
            assertEquals(local(expected.get(priority)), calendar.add(from, Duration.ofMinutes(target.resolveMinutes()), target.continuous()));
        }
        assertEquals(local("2026-10-26T08:15"), calendar.add(from, Duration.ofMinutes(15), true));
    }
    @Test void lunchBeforeAfterShiftWeekendAndExactBoundaries() {
        assertEquals(local("2026-10-26T13:30"), calendar.add(local("2026-10-26T11:00"), Duration.ofHours(1), false));
        assertEquals(local("2026-10-26T08:00"), calendar.add(local("2026-10-26T05:00"), Duration.ofHours(1), false));
        assertEquals(local("2026-10-27T08:00"), calendar.add(local("2026-10-26T18:00"), Duration.ofHours(1), false));
        assertEquals(local("2026-10-26T07:30"), calendar.add(local("2026-10-23T16:30"), Duration.ofHours(1), false));
        assertEquals(local("2026-10-26T11:30"), calendar.add(local("2026-10-26T10:30"), Duration.ofHours(1), false));
        assertEquals(local("2026-10-26T14:00"), calendar.add(local("2026-10-26T11:30"), Duration.ofHours(1), false));
        assertEquals(Duration.ofHours(1), calendar.between(local("2026-10-23T16:30"), local("2026-10-26T07:30"), false));
        assertEquals(local("2026-10-24T01:00"), calendar.add(local("2026-10-23T23:00"), Duration.ofHours(2), true));
    }
    @Test void holidaysTimezoneAndDstUseInstantDurations() {
        var holiday = new BusinessCalendar(calendar.zone(), calendar.days(), calendar.shifts(), Set.of(LocalDate.of(2026, 10, 26)));
        assertEquals(local("2026-10-27T08:00"), holiday.add(local("2026-10-23T17:00"), Duration.ofHours(1), false));
        var dst = new BusinessCalendar("America/New_York", Set.of(DayOfWeek.SUNDAY),
            List.of(new BusinessCalendar.Shift(LocalTime.of(1, 0), LocalTime.of(4, 0))), Set.of());
        var from = Instant.parse("2026-03-08T06:00:00Z");
        assertEquals(Duration.ofHours(2), dst.between(from, Instant.parse("2026-03-08T08:00:00Z"), false));
        assertEquals(Instant.parse("2026-03-08T08:00:00Z"), dst.add(from, Duration.ofHours(2), false));
        assertEquals(Instant.parse("2026-11-01T09:00:00Z"), dst.add(Instant.parse("2026-11-01T05:00:00Z"), Duration.ofHours(4), false));
        var folded = new BusinessCalendar("America/New_York", Set.of(DayOfWeek.SUNDAY),
            List.of(new BusinessCalendar.Shift(LocalTime.of(1, 0), LocalTime.of(1, 30))), Set.of());
        assertFalse(folded.working(Instant.parse("2026-11-01T06:15:00Z")));
        var gap = new BusinessCalendar("America/New_York", Set.of(DayOfWeek.SUNDAY), List.of(
            new BusinessCalendar.Shift(LocalTime.of(2, 15), LocalTime.of(2, 45)),
            new BusinessCalendar.Shift(LocalTime.of(3, 0), LocalTime.of(4, 0))), Set.of());
        assertEquals(Duration.ofHours(1), gap.between(from, Instant.parse("2026-03-08T08:00:00Z"), false));
        assertTrue(gap.working(Instant.parse("2026-03-08T07:20:00Z")));
    }
    @Test void rejectsInvalidConfigurationDurationsAndOverflow() {
        assertThrows(IllegalArgumentException.class, () -> new BusinessCalendar.Shift(LocalTime.NOON, LocalTime.NOON));
        assertThrows(IllegalArgumentException.class, () -> new BusinessCalendar(calendar.zone(), calendar.days(), List.of(
            new BusinessCalendar.Shift(LocalTime.of(7, 0), LocalTime.NOON), new BusinessCalendar.Shift(LocalTime.of(11, 0), LocalTime.of(13, 0))), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new BusinessCalendar(calendar.zone(), Set.of(), calendar.shifts(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> calendar.add(Instant.EPOCH, Duration.ofSeconds(-1), false));
        assertThrows(IllegalArgumentException.class, () -> calendar.add(Instant.EPOCH, Duration.ofSeconds(Long.MAX_VALUE), true));
        assertThrows(IllegalArgumentException.class, () -> calendar.between(Instant.now(), Instant.EPOCH, false));
        assertThrows(IllegalArgumentException.class, () -> new SlaPolicy.Target(60, 30, false));
        assertThrows(IllegalArgumentException.class, () -> new SlaPolicy("bad", calendar, policy.targets(), Double.NaN, HIGH, true));
    }
    @Test void completedTargetsFreezeAndExactDeadlineIsOnTime() {
        var start = local("2026-10-26T09:00");
        var resolve = local("2026-10-27T08:00");
        var at = local("2026-11-02T09:00");
        var view = policy.project(HIGH, start, resolve, start, resolve, null, at);
        assertFalse(view.startBreached()); assertFalse(view.resolveBreached()); assertFalse(view.warning()); assertFalse(view.clockPaused());
        assertTrue(policy.project(HIGH, start, resolve, start.plusSeconds(1), resolve.plusSeconds(1), null, at).startBreached());
        assertTrue(policy.project(HIGH, start, resolve, start, resolve.plusSeconds(1), null, at).resolveBreached());
    }
    @Test void warningAndOutOfHoursAreReadOnlyAndRepeatable() {
        var now = local("2026-10-26T08:50");
        var start = local("2026-10-26T09:00");
        var resolve = local("2026-10-27T08:00");
        var first = policy.project(HIGH, start, resolve, null, null, null, now);
        assertTrue(first.warning()); assertFalse(first.startBreached());
        assertEquals(first, policy.project(HIGH, start, resolve, null, null, null, now));
        assertTrue(policy.project(HIGH, start, resolve, null, null, null, local("2026-10-26T12:00")).clockPaused());
    }
    @Test void pausePoliciesFreezeOnlyBusinessTimeWithoutDoubleCounting() {
        assertTrue(policy.pauses("WAITING_REQUESTER"));
        for (var reason : List.of("WAITING_PARTS", "WAITING_EXTERNAL", "OTHER")) assertFalse(policy.pauses(reason));
        var alternative = new SlaPolicy("other", calendar, policy.targets(), .5, HIGH, false);
        assertTrue(alternative.pauses("WAITING_PARTS")); assertTrue(alternative.pauses("WAITING_EXTERNAL")); assertFalse(alternative.pauses("OTHER"));
        var paused = local("2026-10-23T16:00");
        var due = local("2026-10-26T09:00");
        var now = local("2026-10-26T08:00");
        var view = policy.project(HIGH, paused, due, paused, null, paused, now);
        assertEquals(local("2026-10-26T11:00"), view.resolveBy()); assertTrue(view.clockPaused());
        assertEquals(view, policy.project(HIGH, paused, due, paused, null, paused, now));
        var resumed = policy.project(HIGH, paused, view.resolveBy(), paused, null, null, now);
        assertEquals(view.resolveBy(), resumed.resolveBy()); assertFalse(resumed.clockPaused());
    }
    @Test void escalationIsOneStepCappedBusinessTimeAndNeverBeforeApprovalOrAfterStart() {
        var approved = local("2026-10-23T16:00");
        assertEquals(LOW, policy.escalate(LOW, approved, null, approved.minusSeconds(1)));
        var half = calendar.add(approved, Duration.ofMinutes(255), false);
        assertEquals(LOW, policy.escalate(LOW, approved, null, half));
        assertEquals(MEDIUM, policy.escalate(LOW, approved, null, half.plusSeconds(1)));
        assertEquals(MEDIUM, policy.escalate(LOW, approved, null, half.plusSeconds(1)));
        assertEquals(LOW, policy.escalate(LOW, approved, approved, half.plusSeconds(1)));
        assertEquals(HIGH, policy.escalate(HIGH, approved, null, half));
        assertEquals(CRITICAL, policy.escalate(CRITICAL, approved, null, half));
    }
}
