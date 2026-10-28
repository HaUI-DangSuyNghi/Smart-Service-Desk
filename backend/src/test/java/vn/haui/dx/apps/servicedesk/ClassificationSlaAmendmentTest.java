package vn.haui.dx.apps.servicedesk;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.haui.dx.hpdi.process.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.haui.dx.hpdi.process.api.SlaPolicy.Priority.*;

class ClassificationSlaAmendmentTest {
    private final Instant approved = Instant.parse("2026-10-23T09:00:00Z");
    private final BusinessCalendar calendar = new BusinessCalendar("UTC",
        Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        List.of(new BusinessCalendar.Shift(LocalTime.of(9,0), LocalTime.of(17,0))), Set.of());
    private final SlaPolicy policy = new SlaPolicy("test", calendar, Map.of(
        LOW, new SlaPolicy.Target(120,480,false), MEDIUM, new SlaPolicy.Target(60,240,false),
        HIGH, new SlaPolicy.Target(30,120,false), CRITICAL, new SlaPolicy.Target(15,60,true)), .5, HIGH, true);

    @Test void highToCriticalSubtractsConsumedBudgetInsteadOfRestarting() {
        var now = approved.plusSeconds(20*60);
        var result = ClassificationSlaAmendment.calculate(policy,HIGH,CRITICAL,approved.plusSeconds(30*60),
            approved.plusSeconds(120*60),null,null,now,0,0);
        assertEquals(now.plusSeconds(40*60),result.resolveBy());
        assertEquals(now,result.startBy());
        assertEquals(5*60_000,result.startAdjustmentMillis());
    }

    @Test void businessToContinuousDoesNotChargeTheWeekendRetroactively() {
        var now = Instant.parse("2026-10-24T10:00:00Z");
        var result = ClassificationSlaAmendment.calculate(policy,LOW,CRITICAL,calendar.add(approved,Duration.ofHours(2),false),
            calendar.add(approved,Duration.ofHours(8),false),approved,null,now,0,0);
        assertFalse(result.resolveBy().isAfter(now));
    }

    @Test void continuousToBusinessChargesAllPreviouslyConsumedMinutes() {
        var now = approved.plusSeconds(30*60);
        var result = ClassificationSlaAmendment.calculate(policy,CRITICAL,MEDIUM,approved.plusSeconds(15*60),
            approved.plusSeconds(60*60),approved,null,now,0,0);
        assertEquals(approved.plusSeconds(240*60),result.resolveBy());
    }

    @Test void pausedResolutionUsesPauseAnchorAndRetainsPausedEpisode() {
        var paused = approved.plusSeconds(20*60);
        var result = ClassificationSlaAmendment.calculate(policy,HIGH,CRITICAL,approved.plusSeconds(30*60),
            approved.plusSeconds(120*60),approved,paused,approved.plusSeconds(90*60),0,0);
        assertEquals(paused.plusSeconds(40*60),result.resolveBy());
        var projected = policy.project(CRITICAL,result.startBy(),result.resolveBy(),approved,null,paused,approved.plusSeconds(90*60));
        assertEquals(approved.plusSeconds(130*60),projected.resolveBy());
        assertTrue(projected.clockPaused());
    }

    @Test void decreasingPriorityNeverHidesAnExistingBreach() {
        var due = approved.plusSeconds(60*60);
        var result = ClassificationSlaAmendment.calculate(policy,CRITICAL,LOW,approved.plusSeconds(15*60),
            due,approved,null,approved.plusSeconds(90*60),0,0);
        assertEquals(due,result.resolveBy());
    }

    @Test void repeatedAmendmentsPreserveDebtRatherThanGrantingLostMinutes() {
        var now = approved.plusSeconds(20*60);
        var first = ClassificationSlaAmendment.calculate(policy,HIGH,CRITICAL,approved.plusSeconds(30*60),
            approved.plusSeconds(120*60),null,null,now,0,0);
        var second = ClassificationSlaAmendment.calculate(policy,CRITICAL,MEDIUM,first.startBy(),first.resolveBy(),null,null,now,
            first.startAdjustmentMillis(),first.resolveAdjustmentMillis());
        assertEquals(approved.plusSeconds(60*60),second.startBy());
        assertEquals(approved.plusSeconds(240*60),second.resolveBy());
    }

    @Test void correctionParserRejectsCoercedEnumsNullsAndUnknownFields() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        for (var value : List.of("{\"priority\":0,\"reason\":\"Test\"}","{\"category\":null,\"reason\":\"Test\"}",
                "{\"priority\":\"HIGH\",\"reason\":1}","{\"priority\":\"HIGH\",\"reason\":\"Test\",\"assignee\":\"Other\"}"))
            assertThrows(vn.haui.dx.hpdi.common.api.CapabilityException.class,()->TicketCorrection.parse(json.readTree(value)));
        assertEquals(TicketReview.Priority.HIGH,TicketCorrection.parse(json.readTree("{\"priority\":\"HIGH\",\"reason\":\"Test\"}")).priority());
    }
}
