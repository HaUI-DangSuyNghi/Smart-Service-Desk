package vn.haui.dx.apps.servicedesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Cấu hình riêng Service Desk đang hiệu lực (servicedesk_settings): loại lỗi → tổ (R1), ET mặc định (R2).
 * Chưa đọc được bảng thì dùng giá trị seed trong TicketAssignmentEligibility / TicketReview.
 */
@Service
public class ServiceDeskPolicy {
    private static final long TTL_NANOS = Duration.ofSeconds(5).toNanos();
    private record Cached(JsonNode payload, long loadedAt) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private volatile Cached cached;

    public ServiceDeskPolicy(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc; this.json = json;
    }

    /** Tổ (group Keycloak) xử lý loại lỗi; null với OTHER (điều phối viên tự chọn). */
    public String supportGroup(TicketReview.Category category) {
        var group = payload().path("categoryGroups").path(category.name());
        if (group.isMissingNode()) return TicketAssignmentEligibility.supportGroup(category);
        return group.isNull() ? null : group.asText();
    }

    /** ET mặc định (phút) theo loại × ưu tiên. */
    public int defaultMinutes(TicketReview.Category category, TicketReview.Priority priority) {
        var minutes = payload().path("estimateDefaults").path(category.name()).path(priority.name());
        return minutes.isInt() ? minutes.asInt() : TicketReview.defaultMinutes(category, priority);
    }

    /** Gọi khi lưu cấu hình: xóa bản nhớ sau khi commit. */
    void invalidate() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cached = null;
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cached = null; }
        });
    }

    private JsonNode payload() {
        var value = cached;
        long now = System.nanoTime();
        if (value != null && now - value.loadedAt() < TTL_NANOS) return value.payload();
        var payload = jdbc.query("select payload from servicedesk_settings where id=1", (row, index) -> {
            try { return json.readTree(row.getString("payload")); }
            catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException("Invalid Service Desk settings", failure); }
        }).stream().findFirst().orElse(json.createObjectNode());
        cached = new Cached(payload, now);
        return payload;
    }
}
