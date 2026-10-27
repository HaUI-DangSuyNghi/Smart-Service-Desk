package vn.haui.dx.apps.servicedesk.intelligence.enrichment;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.apps.servicedesk.TicketContextChanged;

@Component
public class EnrichmentEnrollment {
    private final EnrichmentCaseStore store;
    private final EnrichmentConfiguration.Settings settings;

    public EnrichmentEnrollment(EnrichmentCaseStore store, EnrichmentConfiguration.Settings settings) {
        this.store = store;
        this.settings = settings;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void changed(TicketContextChanged event) {
        if (settings.enabled()) {
            store.enqueue(event.workspace(), event.ticketId(), event.contextRevision());
        } else {
            // Rollback flag không được làm kết quả cũ trông như vẫn fresh.
            store.invalidateExisting(event.workspace(), event.ticketId(), event.contextRevision());
        }
    }
}
