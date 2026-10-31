package vn.haui.dx.core.realtime.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.haui.dx.core.realtime.api.RealtimeEvent;
import vn.haui.dx.core.realtime.api.RealtimeEvents;

/** dx.realtime.enabled=false: không mở /ws, client tự quay về polling. */
@Component
@ConditionalOnProperty(name = "dx.realtime.enabled", havingValue = "false")
class NoopRealtimeEvents implements RealtimeEvents {
    @Override public void toUser(String subject, RealtimeEvent event) {}
    @Override public void toTopic(String destination, RealtimeEvent event) {}
}
