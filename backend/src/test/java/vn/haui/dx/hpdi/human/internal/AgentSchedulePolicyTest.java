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

    @Test void shiftRequiredMeansOnlineOnlyInsideAnApprovedSlot() {
        var none=new Schedule("agent",0,false,List.of());
        // Chưa đăng ký ô nào: nghỉ dù công tắc bật
        assertFalse(AgentScheduleService.project(none,true,start,null,true).onDuty());
        assertEquals("OUTSIDE_SHIFT",AgentScheduleService.project(none,true,start,null,true).reason());
        // Trong ô đã duyệt: trực, còn lại tính tới hết ô
        var effective=AgentScheduleService.project(none,true,start,morning,true);
        assertTrue(effective.onDuty());
        assertEquals(270,effective.shiftRemainingMinutes());
        // Tắt trực (nghỉ đột xuất) thắng ô đã đăng ký
        assertFalse(AgentScheduleService.project(none,false,start,morning,true).onDuty());
        // Tắt shiftRequired: ô vẫn được tính, chưa có ô thì theo công tắc tay như cũ
        assertTrue(AgentScheduleService.project(none,true,start,null,false).onDuty());
    }

    @Test void officeHoursFollowConfiguredShiftsOnWorkdaysExceptHolidays() {
        var zone=java.time.ZoneId.of("Asia/Ho_Chi_Minh");
        var base=vn.haui.dx.hpdi.settings.api.WorkPolicy.DEFAULTS;
        // Thứ Năm 05/11/2026, 09:00 giờ Việt Nam: trong ca Sáng, còn 150 phút
        var thursdayMorning=java.time.ZonedDateTime.of(2026,11,5,9,0,0,0,zone).toInstant();
        var shift=AgentScheduleService.officeShift(base,zone,thursdayMorning);
        assertNotNull(shift);
        assertEquals(150,AgentScheduleService.project(new Schedule("agent",0,false,List.of()),true,thursdayMorning,shift,true)
            .shiftRemainingMinutes());
        // Nghỉ trưa, Thứ Bảy, ngày lễ: ngoài giờ hành chính
        assertNull(AgentScheduleService.officeShift(base,zone,java.time.ZonedDateTime.of(2026,11,5,12,0,0,0,zone).toInstant()));
        assertNull(AgentScheduleService.officeShift(base,zone,java.time.ZonedDateTime.of(2026,11,7,9,0,0,0,zone).toInstant()));
        var withHoliday=new vn.haui.dx.hpdi.settings.api.WorkPolicy.Snapshot(base.version(),base.pendingLoadFactor(),
            base.tieThresholdMinutes(),base.maxInProgress(),base.minRemainingMinutes(),base.shifts(),base.targets(),
            base.escalateAfterRatio(),base.maxEscalatedPriority(),base.waitingExternalCounts(),base.criticalReviewAlertMinutes(),
            base.warnRemainingRatio(),base.pendingRemindDays(),base.reopenUnclaimedMinutes(),base.autoCloseAfterDays(),
            base.estimateMinSamples(),base.estimateWindowMonths(),base.estimateOutlierMinMinutes(),base.estimateOutlierMaxMinutes(),
            false,false,List.of(new vn.haui.dx.hpdi.settings.api.WorkPolicy.Holiday(java.time.LocalDate.of(2026,11,5),"Lễ thử")));
        assertNull(AgentScheduleService.officeShift(withHoliday,zone,thursdayMorning));
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
