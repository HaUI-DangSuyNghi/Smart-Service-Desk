package vn.haui.dx.hpdi.human.api;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Trung tâm thông báo (chuông) và email cho mọi quy trình. App gọi {@link #notify} ở mỗi lần đổi trạng thái;
 * người nhận là các subject chỉ định và/hoặc mọi điều phối viên của workspace, trừ chính người làm thao tác.
 */
public interface Notifications {
    enum Severity { INFO, WARNING, CRITICAL }

    /**
     * type: mã loại viết hoa (TICKET_CREATED...); link: đường dẫn trong web (vd /servicedesk/tickets/x), null nếu không có.
     * email: ngoài chuông có gửi thêm email không. actor: người làm thao tác; null thì lấy người đang gọi (nếu có).
     */
    record Notice(String workspace, String type, Severity severity, String title, String body, String link,
                  String processKey, String businessKey, String actor, Set<String> subjects, boolean coordinators, boolean email) {
        public Notice { subjects = subjects == null ? Set.of() : Set.copyOf(subjects); }
    }

    record Item(UUID id, String type, String category, String severity, String title, String body, String link,
                String processKey, String businessKey, Instant createdAt, Instant readAt) {}
    record Inbox(List<Item> items, int page, int size, long total, long unread) {
        public Inbox { items = List.copyOf(items); }
    }

    /** Ghi trong transaction đang có (thao tác lỗi thì không có thông báo); không có người nhận thì bỏ qua. */
    void notify(Notice notice);
    Inbox list(String workspace, boolean unreadOnly, int page, int size);
    void read(String workspace, UUID id);
    void readAll(String workspace);
}
