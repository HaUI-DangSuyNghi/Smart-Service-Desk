package vn.haui.dx.core.realtime.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.haui.dx.core.realtime.api.RealtimeEvent;
import vn.haui.dx.core.realtime.api.RealtimeEvents;

@Component
@ConditionalOnProperty(name = "dx.realtime.enabled", havingValue = "true", matchIfMissing = true)
class StompRealtimeEvents implements RealtimeEvents {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(StompRealtimeEvents.class);

    // Lấy lười để dịch vụ nghiệp vụ không kéo cả cấu hình broker vào lúc khởi tạo
    private final ObjectProvider<SimpMessagingTemplate> template;

    StompRealtimeEvents(ObjectProvider<SimpMessagingTemplate> template) { this.template = template; }

    @Override
    public void toUser(String subject, RealtimeEvent event) {
        if (subject == null || event == null) return;
        send("user:" + subject + key(event), () -> template.getObject().convertAndSendToUser(subject, USER_QUEUE, event));
    }

    @Override
    public void toTopic(String destination, RealtimeEvent event) {
        if (destination == null || !destination.startsWith("/topic/") || event == null) return;
        send(destination + key(event), () -> template.getObject().convertAndSend(destination, event));
    }

    private static String key(RealtimeEvent event) {
        return "|" + event.type() + "|" + event.workspace() + "|" + event.resource() + "|" + event.id();
    }

    private void send(String key, Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { run(action); return; }
        batch().pending.putIfAbsent(key, action);
    }

    // Mỗi transaction một lô (tìm theo danh sách synchronization hiện tại nên REQUIRES_NEW có lô riêng)
    private Batch batch() {
        for (var synchronization : TransactionSynchronizationManager.getSynchronizations())
            if (synchronization instanceof Batch batch && batch.owner() == this) return batch;
        var batch = new Batch();
        TransactionSynchronizationManager.registerSynchronization(batch);
        return batch;
    }

    private void run(Runnable action) {
        try { action.run(); }
        catch (RuntimeException failure) { LOG.warn("Realtime event was not delivered: {}", failure.getMessage()); }
    }

    private final class Batch implements TransactionSynchronization {
        private final Map<String, Runnable> pending = new LinkedHashMap<>();
        StompRealtimeEvents owner() { return StompRealtimeEvents.this; }
        @Override public void afterCommit() { pending.values().forEach(StompRealtimeEvents.this::run); }
    }
}
