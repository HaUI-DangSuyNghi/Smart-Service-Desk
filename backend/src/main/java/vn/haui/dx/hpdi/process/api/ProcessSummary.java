package vn.haui.dx.hpdi.process.api;

import java.time.Instant;
import java.util.List;

/**
 * Một yêu cầu người gọi đã tạo, cho trang "Yêu cầu của tôi".
 *
 * @param activeTaskKeys bước đang mở; rỗng khi đã kết thúc
 */
public record ProcessSummary(String id, String processKey, String businessKey, Instant startedAt, Instant endedAt,
                             List<String> activeTaskKeys) {
    public ProcessSummary { activeTaskKeys = List.copyOf(activeTaskKeys); }
}
