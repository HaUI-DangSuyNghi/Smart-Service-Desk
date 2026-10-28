package vn.haui.dx.hpdi.human.internal;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.human.api.AlertProcessing;

@Component
@EnableScheduling
public class AlertDeliveryWorker {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AlertDeliveryWorker.class);
    private final JdbcTemplate jdbc;
    private final AlertProcessing processing;
    private final AlertDelivery delivery;
    private final boolean enabled;
    public AlertDeliveryWorker(JdbcTemplate jdbc, AlertProcessing processing, AlertDelivery delivery, @Value("${hpdi.alerts.scheduled:true}") boolean enabled) {
        this.jdbc = jdbc; this.processing = processing; this.delivery = delivery; this.enabled = enabled;
    }
    @Scheduled(fixedDelayString = "${hpdi.alerts.poll-millis:5000}", initialDelayString = "${hpdi.alerts.poll-millis:5000}")
    public void run() {
        if (!enabled) return;
        var events = jdbc.queryForList("update hpdi_alert_event set retry_at=current_timestamp+interval '1 minute' where id in"
            + " (select id from hpdi_alert_event where delivered_at is null and (not agent_suppressed or coordinators) and retry_at<=current_timestamp order by retry_at,id limit 100 for update skip locked)"
            + " returning id,workspace,coordinators,case when agent_suppressed then null else agent end as agent");
        for (var event : events) {
            String workspace = (String) event.get("workspace");
            try { processing.run(workspace, () -> delivery.deliver(workspace, (UUID) event.get("id"),
                delivery.recipients(workspace, (Boolean) event.get("coordinators"), (String) event.get("agent")))); }
            catch (RuntimeException failure) { LOG.warn("Alert delivery deferred for event {}", event.get("id")); }
        }
    }
}
