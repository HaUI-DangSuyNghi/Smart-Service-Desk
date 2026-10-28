package vn.haui.dx.apps.servicedesk;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.haui.dx.hpdi.process.api.SlaPolicy;
import vn.haui.dx.hpdi.settings.api.WorkPolicy;
import static org.junit.jupiter.api.Assertions.*;

/** Giá trị Cấu hình hệ thống đi vào đúng công thức runtime; giá trị seed giữ nguyên hành vi cũ. */
class WorkSettingsRuntimeTest {
    @Test void remainingLoadUsesMinimumAndPendingFactor() {
        // Seed: tối thiểu 10 phút, Đang chờ tính một nửa làm tròn lên như công thức (remaining+1)/2
        assertEquals(60, TicketReviewService.remainingMinutes("NOT_STARTED", 60, 0, 10, 0.5));
        assertEquals(10, TicketReviewService.remainingMinutes("IN_PROGRESS", 60, 55, 10, 0.5));
        assertEquals(23, TicketReviewService.remainingMinutes("PENDING", 60, 15, 10, 0.5));
        // Cấu hình khác
        assertEquals(30, TicketReviewService.remainingMinutes("IN_PROGRESS", 60, 55, 30, 0.5));
        assertEquals(3, TicketReviewService.remainingMinutes("PENDING", 10, 0, 10, 0.3));
        assertEquals(0, TicketReviewService.remainingMinutes("PENDING", 60, 0, 10, 0));
    }

    @Test void tieThresholdControlsTheLoadBand() {
        var light = candidate("a", 0, 5);
        var heavier = candidate("b", 10, 0);
        // Chênh 10 phút < 15: cùng nhóm, ít việc hôm nay hơn được chọn
        assertEquals("b", AssignmentSuggestionController.rank(List.of(light, heavier), null, 15).get(0).subject());
        // Ngưỡng 5: khác nhóm, người ít tải được chọn
        assertEquals("a", AssignmentSuggestionController.rank(List.of(light, heavier), null, 5).get(0).subject());
        assertEquals("a", AssignmentSuggestionController.rank(List.of(light, heavier), null, 0).get(0).subject());
    }

    @Test void slaPolicyFollowsSettingsAndSeedMatchesOldPolicy() {
        var seed = TicketSlaConfiguration.policy(WorkPolicy.DEFAULTS, "Asia/Ho_Chi_Minh",
            Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), Set.of());
        assertEquals(new SlaPolicy.Target(15, 120, true), seed.targets().get(SlaPolicy.Priority.CRITICAL));
        assertEquals(new SlaPolicy.Target(60, 480, false), seed.targets().get(SlaPolicy.Priority.HIGH));
        assertEquals(new SlaPolicy.Target(240, 1020, false), seed.targets().get(SlaPolicy.Priority.MEDIUM));
        assertEquals(new SlaPolicy.Target(510, 2550, false), seed.targets().get(SlaPolicy.Priority.LOW));
        assertEquals(0.5, seed.escalateAfterRatio());
        assertEquals(SlaPolicy.Priority.HIGH, seed.maxEscalatedPriority());
        assertEquals(0.2, seed.warnRemainingRatio());
        assertFalse(seed.pauses("WAITING_PARTS"));
        assertEquals(2, seed.calendar().shifts().size());
        assertTrue(seed.version().startsWith("r5-settings-v"));
    }

    @Test void oldSnapshotsWithoutWarningRatioKeepTwentyPercent() {
        var old = new SlaPolicy("r5-v1", TicketSlaConfiguration.policy(WorkPolicy.DEFAULTS, "UTC", Set.of(DayOfWeek.MONDAY), Set.of()).calendar(),
            TicketSlaConfiguration.policy(WorkPolicy.DEFAULTS, "UTC", Set.of(DayOfWeek.MONDAY), Set.of()).targets(), .5, SlaPolicy.Priority.HIGH, true, null);
        assertEquals(SlaPolicy.DEFAULT_WARN_RATIO, old.warnRemainingRatio());
    }

    private static AssignmentSuggestionController.Candidate candidate(String subject, long load, long today) {
        return new AssignmentSuggestionController.Candidate(subject, subject, true, load, today, null, false, null);
    }
}
