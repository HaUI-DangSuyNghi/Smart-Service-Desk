package vn.haui.dx.apps.servicedesk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.human.api.AlertProcessing;

@Component
public class TicketAlertWorker {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TicketAlertWorker.class);
    private final JdbcTemplate jdbc;
    private final TicketAlerts alerts;
    private final AlertProcessing processing;
    private final boolean enabled;
    public TicketAlertWorker(JdbcTemplate jdbc, TicketAlerts alerts, AlertProcessing processing, @Value("${service-desk.alerts.scheduled:true}") boolean enabled) {
        this.jdbc = jdbc; this.alerts = alerts; this.processing = processing; this.enabled = enabled;
    }
    @Scheduled(fixedDelayString = "${service-desk.alerts.poll-millis:60000}", initialDelayString = "${service-desk.alerts.poll-millis:60000}")
    public void run() {
        if (!enabled) return;
        for (var row : jdbc.queryForList("select workspace,process_instance_id,handle_task_id,category from servicedesk_alert_monitor where active order by checked_at,workspace,handle_task_id limit 100")) {
            String workspace = (String) row.get("workspace");
            String task = (String) row.get("handle_task_id");
            jdbc.update("update servicedesk_alert_monitor set checked_at=current_timestamp where workspace=? and handle_task_id=?", workspace, task);
            try { processing.run(workspace, () -> alerts.check(workspace, (String) row.get("process_instance_id"), task,
                "OTHER".equals(row.get("category")) ? null : processing.duty(workspace, TicketAssignmentEligibility.supportGroup(TicketReview.Category.valueOf((String) row.get("category")))),
                processing.duty(workspace, null), (String) row.get("category"))); }
            catch (RuntimeException failure) { LOG.warn("Ticket alert evaluation deferred for task {}", task); }
        }
    }
}
