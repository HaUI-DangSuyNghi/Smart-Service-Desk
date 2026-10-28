package vn.haui.dx.apps.servicedesk;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.haui.dx.hpdi.settings.api.WorkPolicy;

/**
 * ET mặc định khi duyệt (R2): đủ mẫu thì dùng trung vị thời gian làm thực tế của việc đã đóng
 * cùng loại × ưu tiên trong workspace (giai đoạn 2), không thì bảng mặc định (giai đoạn 1).
 * ET đã gán cho ticket không tự đổi theo khi có thêm dữ liệu.
 */
@Service
public class TicketEstimates {
    /** basis: DEFAULT (bảng), SIMILAR (trung vị); sampleSize chỉ có khi SIMILAR. */
    public record Estimate(int minutes, String basis, Integer sampleSize) {}

    private final JdbcTemplate jdbc;
    private final WorkPolicy work;
    private final ServiceDeskPolicy policy;
    private final Clock clock;

    public TicketEstimates(JdbcTemplate jdbc, WorkPolicy work, ServiceDeskPolicy policy,
                           org.springframework.beans.factory.ObjectProvider<Clock> clock) {
        this.jdbc = jdbc; this.work = work; this.policy = policy; this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    public Estimate estimate(String workspace, TicketReview.Category category, TicketReview.Priority priority) {
        var settings = work.current();
        var since = clock.instant().atOffset(ZoneOffset.UTC).minusMonths(settings.estimateWindowMonths()).toInstant();
        // Bỏ giá trị bất thường (thường do quên bấm Bắt đầu / Tạm dừng); trung vị để vài việc rất dài không kéo lệch
        var sample = jdbc.queryForMap("select count(*) as samples,"
            + " percentile_cont(0.5) within group (order by t.worked_millis) as median"
            + " from servicedesk_ticket t join servicedesk_review r on r.ticket_id=t.id"
            + " where t.workspace=? and r.category=? and r.priority=? and t.worked_millis between ? and ?"
            + " and exists(select 1 from servicedesk_confirmation c where c.workspace=t.workspace and c.ticket_id=t.id"
            + " and c.decision in ('REQUESTER','AUTO') and c.decided_at>=?)",
            workspace, category.name(), priority.name(),
            settings.estimateOutlierMinMinutes() * 60_000L, settings.estimateOutlierMaxMinutes() * 60_000L, Timestamp.from(since));
        long samples = ((Number) sample.get("samples")).longValue();
        if (samples >= settings.estimateMinSamples() && sample.get("median") != null) {
            int minutes = (int) Math.min(10080, Math.max(1, Math.ceil(((Number) sample.get("median")).doubleValue() / 60_000)));
            return new Estimate(minutes, "SIMILAR", (int) Math.min(Integer.MAX_VALUE, samples));
        }
        return new Estimate(policy.defaultMinutes(category, priority), "DEFAULT", null);
    }
}
