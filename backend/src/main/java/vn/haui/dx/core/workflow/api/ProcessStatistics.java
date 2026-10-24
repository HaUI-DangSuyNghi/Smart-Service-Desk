package vn.haui.dx.core.workflow.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Số liệu quy trình của một workspace cho dashboard.
 * Nhóm "đợt" ({@code started}, {@code running}, {@code completed}) tính trên các quy trình
 * <b>bắt đầu</b> trong [from, to); nhóm "việc" ({@code waitingClaim}, {@code inProgress}) là hiện trạng lúc gọi.
 */
public record ProcessStatistics(Instant from, Instant to, List<ProcessCount> processes, List<DailyCount> daily) {
    public ProcessStatistics {
        processes = List.copyOf(processes);
        daily = List.copyOf(daily);
    }

    /**
     * @param completed kết thúc bình thường (không bị xóa/hủy)
     * @param averageCompletionSeconds thời gian từ lúc tạo tới lúc kết thúc, trung bình; null khi chưa có quy trình nào xong
     * @param waitingClaim việc đang mở chưa ai nhận
     * @param inProgress việc đang mở đã có người giữ
     */
    public record ProcessCount(String processKey, long started, long running, long completed,
                               Long averageCompletionSeconds, long waitingClaim, long inProgress) {}

    /** Số quy trình bắt đầu trong một ngày (theo múi giờ người xem). */
    public record DailyCount(LocalDate date, long started) {}
}
