package vn.haui.dx.apps.servicedesk;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Nhãn AI gán vào ticket khi chạy nền xong: loại, ưu tiên, ET. Chưa duyệt thì ticket mang nhãn này;
 * điều phối viên duyệt / sửa thì servicedesk_review thay thế. AI không tính được ưu tiên thì để mặc định;
 * ET tính như lúc duyệt (R2), chưa có loại thì không tính.
 */
@Service
public class TicketLabels {
    public static final String DEFAULT_PRIORITY = "MEDIUM";

    public record Label(String category, String priority, boolean priorityDefaulted, String impact, String urgency,
                        TicketEstimates.Estimate estimate, Instant labeledAt) {}

    private final JdbcTemplate jdbc;
    private final TicketEstimates estimates;

    public TicketLabels(JdbcTemplate jdbc, TicketEstimates estimates) {
        this.jdbc = jdbc; this.estimates = estimates;
    }

    private TicketRealtime realtime;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRealtime(TicketRealtime realtime) { this.realtime = realtime; }

    /** Ghi đè nhãn cũ (chạy lại, bổ sung thông tin thì nhãn theo kết quả mới nhất). */
    public void save(String workspace, UUID ticketId, String category, String priority, String impact, String urgency) {
        var knownCategory = parse(TicketReview.Category.class, category);
        var knownPriority = parse(TicketReview.Priority.class, priority);
        boolean defaulted = knownPriority == null;
        var effective = defaulted ? TicketReview.Priority.valueOf(DEFAULT_PRIORITY) : knownPriority;
        var estimate = knownCategory == null ? null : estimates.estimate(workspace, knownCategory, effective);
        jdbc.update("insert into servicedesk_ticket_label(ticket_id,category,priority,priority_defaulted,impact,urgency,"
            + "estimate_minutes,estimate_basis,estimate_samples) values(?,?,?,?,?,?,?,?,?) on conflict(ticket_id) do update set"
            + " category=excluded.category,priority=excluded.priority,priority_defaulted=excluded.priority_defaulted,"
            + "impact=excluded.impact,urgency=excluded.urgency,estimate_minutes=excluded.estimate_minutes,"
            + "estimate_basis=excluded.estimate_basis,estimate_samples=excluded.estimate_samples,labeled_at=clock_timestamp()",
            ticketId, knownCategory == null ? null : knownCategory.name(), effective.name(), defaulted, impact, urgency,
            estimate == null ? null : estimate.minutes(), estimate == null ? null : estimate.basis(),
            estimate == null ? null : estimate.sampleSize());
        if (realtime != null) realtime.changed(workspace, ticketId);
    }

    public Label read(UUID ticketId) {
        var rows = jdbc.query("select * from servicedesk_ticket_label where ticket_id=?", (row, index) -> {
            Integer minutes = (Integer) row.getObject("estimate_minutes");
            return new Label(row.getString("category"), row.getString("priority"), row.getBoolean("priority_defaulted"),
                row.getString("impact"), row.getString("urgency"),
                minutes == null ? null : new TicketEstimates.Estimate(minutes, row.getString("estimate_basis"),
                    (Integer) row.getObject("estimate_samples")),
                row.getTimestamp("labeled_at").toInstant());
        }, ticketId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null) return null;
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException unknown) { return null; }
    }
}
