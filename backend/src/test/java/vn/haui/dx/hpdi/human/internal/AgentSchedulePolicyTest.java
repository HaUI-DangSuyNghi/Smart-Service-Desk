package vn.haui.dx.hpdi.human.internal;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.haui.dx.hpdi.human.api.AgentSchedules.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentSchedulePolicyTest {
    private final Instant start=Instant.parse("2026-10-28T00:00:00Z");
    private final Shift morning=new Shift(start,start.plusSeconds(16200),"Asia/Ho_Chi_Minh");
    private final Schedule enabled=new Schedule("agent",1,true,List.of(morning));

    @Test void halfOpenBoundariesAndRealRemainingMinutes() {
        assertFalse(AgentScheduleService.project(enabled,true,start.minusNanos(1)).onDuty());
        assertTrue(AgentScheduleService.project(enabled,true,start).onDuty());
        assertEquals(270,AgentScheduleService.project(enabled,true,start).shiftRemainingMinutes());
        assertEquals(20,AgentScheduleService.project(enabled,true,morning.endsAt().minusSeconds(1200)).shiftRemainingMinutes());
        assertFalse(AgentScheduleService.project(enabled,true,morning.endsAt()).onDuty());
    }

    @Test void manualOffAlwaysWinsAndLegacyIsUnchanged() {
        assertFalse(AgentScheduleService.project(enabled,false,start).onDuty());
        assertEquals("MANUAL_OFF_DUTY",AgentScheduleService.project(enabled,false,start).reason());
        var legacy=new Schedule("agent",0,false,List.of());
        assertTrue(AgentScheduleService.project(legacy,true,start).onDuty());
        assertFalse(AgentScheduleService.project(legacy,false,start).onDuty());
        assertNull(AgentScheduleService.project(legacy,true,start).shiftRemainingMinutes());
    }

    @Test void enabledEmptyScheduleIsExplicitTimeOff() {
        assertFalse(AgentScheduleService.project(new Schedule("agent",2,true,List.of()),true,start).onDuty());
        assertTrue(AgentScheduleService.project(new Schedule("agent",2,false,List.of(morning)),true,start.plusSeconds(86400)).onDuty());
    }

    @Test void rejectsOverlapButAllowsAdjacentAndSplitShifts() {
        var adjacent=new Shift(morning.endsAt(),morning.endsAt().plusSeconds(3600),morning.timezone());
        assertEquals(List.of(morning,adjacent),AgentScheduleService.validate(List.of(adjacent,morning)));
        assertThrows(RuntimeException.class,() -> AgentScheduleService.validate(List.of(morning,morning)));
    }

    @Test void rejectsMalformedZoneDurationAndNulls() {
        assertThrows(RuntimeException.class,() -> AgentScheduleService.validate(List.of(new Shift(start,start,"UTC"))));
        assertThrows(RuntimeException.class,() -> AgentScheduleService.validate(List.of(new Shift(start,start.plusSeconds(90000),"UTC"))));
        assertThrows(RuntimeException.class,() -> AgentScheduleService.validate(List.of(new Shift(start,start.plusSeconds(60),"Mars/Nowhere"))));
        assertThrows(RuntimeException.class,() -> AgentScheduleService.validate(java.util.Arrays.asList((Shift)null)));
    }
}
