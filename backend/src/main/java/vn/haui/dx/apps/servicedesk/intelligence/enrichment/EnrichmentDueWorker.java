package vn.haui.dx.apps.servicedesk.intelligence.enrichment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Timer bền vững đọc deadline DB; không lưu actor/token hoặc gọi model khi chạy nền. */
@Component
public class EnrichmentDueWorker {
    private final EnrichmentCaseStore store;
    private final EnrichmentConfiguration.Settings settings;

    public EnrichmentDueWorker(EnrichmentCaseStore store, EnrichmentConfiguration.Settings settings) {
        this.store = store;
        this.settings = settings;
    }

    @Scheduled(fixedDelayString = "${serviceDesk.enrichment.poll-delay-ms:60000}")
    public void poll() {
        if (settings.enabled() && settings.timersEnabled()) {
            store.expireDue();
        }
    }
}
