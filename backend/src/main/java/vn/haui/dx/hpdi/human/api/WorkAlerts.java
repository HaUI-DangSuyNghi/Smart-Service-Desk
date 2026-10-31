package vn.haui.dx.hpdi.human.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.haui.dx.hpdi.common.api.PageRequest;

public interface WorkAlerts {
    /** CRITICAL_UNREVIEWED: việc khẩn cấp quá hạn cấu hình mà chưa ai duyệt (R7, chỉ nhắc, không tự duyệt). */
    enum Type { START_OVERDUE, SLA_WARNING, SLA_BREACHED, PENDING_TOO_LONG, CRITICAL_NEW, NO_ONE_ON_DUTY, ASSIGNEE_OFF_DUTY, CLASSIFICATION_CHANGED, REOPENED, REOPEN_UNCLAIMED, CRITICAL_UNREVIEWED }
    enum Severity { INFO, WARNING, CRITICAL }
    record Event(String workspace, Type type, Severity severity, String processInstanceId, String processKey,
                 String businessKey, String taskId, String generation, String label, boolean coordinators, String agent) {}
    record Alert(UUID id, Type type, Severity severity, String processInstanceId, String processKey,
                 String businessKey, String taskId, String label, Instant createdAt, Instant readAt) {}
    record Inbox(List<Alert> items, int page, int size, long total, long unread) {
        public Inbox { items = List.copyOf(items); }
    }
    void publish(Event event);
    default void suppressAgent(String workspace, String taskId) { throw new UnsupportedOperationException("Alert suppression not supported"); }
    Inbox list(String workspace, Instant since, boolean unreadOnly, PageRequest page);
    void read(String workspace, UUID id);
}
