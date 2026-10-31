package vn.haui.dx.hpdi.process.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Số liệu quy trình cho dashboard. {@code started}/{@code running}/{@code completed} tính trên các yêu cầu
 * tạo trong [from, to); {@code waitingClaim}/{@code inProgress} là việc đang mở lúc gọi.
 */
public record ProcessStatistics(Instant from, Instant to, List<ProcessCount> processes, List<DailyCount> daily) {
    public ProcessStatistics {
        processes = List.copyOf(processes);
        daily = List.copyOf(daily);
    }

    /** @param averageCompletionSeconds null khi chưa có yêu cầu nào xong trong khoảng */
    public record ProcessCount(String processKey, long started, long running, long completed,
                               Long averageCompletionSeconds, long waitingClaim, long inProgress) {}

    public record DailyCount(LocalDate date, long started) {}
}
