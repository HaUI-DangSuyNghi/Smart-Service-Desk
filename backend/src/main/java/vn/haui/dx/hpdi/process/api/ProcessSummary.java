package vn.haui.dx.hpdi.process.api;

import java.time.Instant;
import java.util.List;

/**
 * Một yêu cầu trong danh sách "Yêu cầu" (của tôi hoặc mọi yêu cầu tôi được xem).
 *
 * @param initiator subject người tạo
 * @param activeTaskKeys bước đang mở; rỗng khi đã kết thúc
 */
public record ProcessSummary(String id, String processKey, String businessKey, String initiator, Instant startedAt,
                             Instant endedAt, List<String> activeTaskKeys, boolean cancelled) {
    public ProcessSummary(String id, String processKey, String businessKey, String initiator, Instant startedAt,
            Instant endedAt, List<String> activeTaskKeys) {
        this(id, processKey, businessKey, initiator, startedAt, endedAt, activeTaskKeys, false);
    }
    public ProcessSummary { activeTaskKeys = List.copyOf(activeTaskKeys); }
}
