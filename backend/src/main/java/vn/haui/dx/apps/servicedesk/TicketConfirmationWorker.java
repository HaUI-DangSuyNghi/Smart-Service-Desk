package vn.haui.dx.apps.servicedesk;

import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.process.api.ProcessTimers;

@Component
public class TicketConfirmationWorker {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TicketConfirmationWorker.class);
    private final JdbcTemplate jdbc;
    private final ProcessTimers timers;
    private final Clock clock;
    private final boolean enabled;
    public TicketConfirmationWorker(JdbcTemplate jdbc, ProcessTimers timers, Clock clock,
            @Value("${serviceDesk.confirmation.scheduled:true}") boolean enabled) {
        this.jdbc = jdbc; this.timers = timers; this.clock = clock; this.enabled = enabled;
    }
    @Scheduled(fixedDelayString = "${serviceDesk.confirmation.poll-ms:30000}", initialDelayString = "${serviceDesk.confirmation.poll-ms:30000}")
    public void run() {
        if (!enabled) return;
        var now = Timestamp.from(clock.instant());
        var rows = jdbc.queryForList("select workspace,process_instance_id,generation from servicedesk_confirmation"
            + " where (decision is null and deadline_at<=?) or (decision='REWORK' and reopen_received_at is null and reopen_deadline_at<=?)"
            + " order by checked_at,workspace,generation limit 100", now, now);
        for (var row : rows) {
            String workspace = (String) row.get("workspace"), process = (String) row.get("process_instance_id"), generation = (String) row.get("generation");
            try { timers.evaluate(workspace, process, generation); }
            catch (RuntimeException failure) {
                LOG.warn("Confirmation evaluation deferred: workspace={}, process={}, cause={}", workspace, process, failure.getClass().getSimpleName());
            } finally {
                jdbc.update("update servicedesk_confirmation set checked_at=? where workspace=? and process_instance_id=? and generation=?",
                    now, workspace, process, generation);
            }
        }
    }
}
