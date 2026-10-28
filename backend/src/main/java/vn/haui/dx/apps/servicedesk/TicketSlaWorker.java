package vn.haui.dx.apps.servicedesk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TicketSlaWorker {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TicketSlaWorker.class);
    private final JdbcTemplate jdbc;
    private final TicketSlaService sla;
    private final boolean enabled;
    public TicketSlaWorker(JdbcTemplate jdbc, TicketSlaService sla, @Value("${service-desk.sla.scheduled:true}") boolean enabled) {
        this.jdbc = jdbc; this.sla = sla; this.enabled = enabled;
    }
    @Scheduled(fixedDelayString = "${service-desk.sla.poll-millis:60000}", initialDelayString = "${service-desk.sla.poll-millis:60000}")
    public void run() {
        if (!enabled) return;
        for (var row : jdbc.queryForList("select workspace,process_instance_id,handle_task_id from servicedesk_sla where resolved_at is null and released_at is null order by checked_at,workspace,handle_task_id limit 100")) {
            jdbc.update("update servicedesk_sla set checked_at=current_timestamp where workspace=? and handle_task_id=?",
                row.get("workspace"), row.get("handle_task_id"));
            try { sla.reconcile((String) row.get("workspace"), (String) row.get("process_instance_id"), (String) row.get("handle_task_id")); }
            catch (RuntimeException failure) { LOG.error("SLA reconciliation failed for task {}", row.get("handle_task_id"), failure); }
        }
    }
}
