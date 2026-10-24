package vn.haui.dx.core.workflow.api;

import java.time.Instant;
import java.util.List;

/** Một quy trình người dùng đã khởi tạo; {@code activeTaskKeys} rỗng khi đã kết thúc. */
public record ProcessSummary(String id, String processKey, String businessKey, Instant startedAt, Instant endedAt,
                             List<String> activeTaskKeys) {
    public ProcessSummary { activeTaskKeys = List.copyOf(activeTaskKeys); }
}
