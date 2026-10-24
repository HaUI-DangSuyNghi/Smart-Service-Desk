package vn.haui.dx.core.workflow.api;

import java.time.Instant;
import java.util.List;

/**
 * Một quy trình trong danh sách yêu cầu.
 *
 * @param initiator subject người tạo
 * @param activeTaskKeys bước đang mở; rỗng khi đã kết thúc
 */
public record ProcessSummary(String id, String processKey, String businessKey, String initiator, Instant startedAt,
                             Instant endedAt, List<String> activeTaskKeys) {
    public ProcessSummary { activeTaskKeys = List.copyOf(activeTaskKeys); }
}
