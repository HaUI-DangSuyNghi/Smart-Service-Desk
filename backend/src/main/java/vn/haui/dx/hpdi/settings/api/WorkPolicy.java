package vn.haui.dx.hpdi.settings.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Cấu hình chung đang hiệu lực (màn Cấu hình hệ thống, mục 6 plan nghiệp vụ), đọc cho runtime.
 * Không kiểm quyền quản trị: chỉ đọc giá trị đã được duyệt lưu. Lưu mới thì lần đọc sau thấy ngay.
 */
public interface WorkPolicy {
    record Shift(String name, LocalTime start, LocalTime end) {}
    record Target(int startWithinMinutes, int resolveWithinMinutes) {}
    /** Ngày lễ được nghỉ: không tính hạn SLA, người làm hành chính nghỉ; name là ghi chú hiện trên lịch. */
    record Holiday(LocalDate date, String name) {}

    record Snapshot(long version,
                    // R2 chọn người
                    double pendingLoadFactor, int tieThresholdMinutes, int maxInProgress, int minRemainingMinutes,
                    // R3 giờ làm việc, dùng làm lịch tính hạn
                    List<Shift> shifts,
                    // R5 hạn xử lý; khóa LOW..CRITICAL
                    Map<String, Target> targets, double escalateAfterRatio, String maxEscalatedPriority,
                    boolean waitingExternalCounts,
                    // R7: CRITICAL chưa duyệt sau bấy nhiêu phút thì nhắc lại (không tự duyệt)
                    int criticalReviewAlertMinutes,
                    // R8, R11
                    double warnRemainingRatio, int pendingRemindDays, int reopenUnclaimedMinutes, int autoCloseAfterDays,
                    // R2 ET theo trung vị việc đã đóng
                    int estimateMinSamples, int estimateWindowMonths, int estimateOutlierMinMinutes, int estimateOutlierMaxMinutes,
                    // R3 đăng ký ca: cho đăng ký T7/CN, ca kỹ thuật viên tự đăng ký cần quản lý duyệt
                    boolean weekendRegistration, boolean scheduleApproval,
                    // Ngày lễ (V40), dùng chung cho lịch tính hạn và ca trực
                    List<Holiday> holidays) {
        public Snapshot {
            shifts = List.copyOf(shifts);
            targets = Map.copyOf(targets);
            holidays = List.copyOf(holidays);
        }
    }

    /** Giá trị seed (V32), dùng khi chưa đọc được bảng cấu hình và cho test tự khởi tạo service. */
    Snapshot DEFAULTS = new Snapshot(1, 0.5, 15, 1, 10,
        List.of(new Shift("Morning", LocalTime.of(7, 0), LocalTime.of(11, 30)),
            new Shift("Afternoon", LocalTime.of(13, 0), LocalTime.of(17, 0))),
        Map.of("LOW", new Target(510, 2550), "MEDIUM", new Target(240, 1020),
            "HIGH", new Target(60, 480), "CRITICAL", new Target(15, 120)),
        0.5, "HIGH", true, 5, 0.2, 3, 15, 3, 20, 6, 2, 480, false, false, List.of());

    Snapshot current();
}
