package vn.haui.dx.hpdi.human.internal;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import vn.haui.dx.core.realtime.api.RealtimeEvent;
import vn.haui.dx.core.realtime.api.RealtimeEvents;
import vn.haui.dx.core.realtime.api.SubscriptionGuard;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.human.api.LiveChannelGuard;
import vn.haui.dx.hpdi.human.api.LiveUpdates;

/** Cầu nối app → WebSocket của Core: đặt tên kênh theo workspace, kiểm thành viên rồi hỏi guard của app. */
@Component
public class RealtimeLiveUpdates implements LiveUpdates, SubscriptionGuard {
    private static final String PREFIX = "/topic/workspaces/";
    private static final Pattern DESTINATION =
        Pattern.compile("^/topic/workspaces/([a-z0-9-]{1,50})/([a-z0-9-]{1,40}(?:/[A-Za-z0-9-]{1,64}){0,3})$");

    private final RealtimeEvents events;
    private final HumanContext humans;
    private final ObjectProvider<LiveChannelGuard> guards;

    public RealtimeLiveUpdates(RealtimeEvents events, HumanContext humans, ObjectProvider<LiveChannelGuard> guards) {
        this.events = events; this.humans = humans; this.guards = guards;
    }

    @Override
    public void toUser(String workspace, String subject, String type, String resource, String id) {
        events.toUser(subject, RealtimeEvent.of(type, workspace, resource, id));
    }

    @Override
    public void toChannel(String workspace, String channel, String type, String resource, String id) {
        String destination = PREFIX + workspace + "/" + channel;
        if (DESTINATION.matcher(destination).matches())
            events.toTopic(destination, RealtimeEvent.of(type, workspace, resource, id));
    }

    @Override
    public boolean supports(String destination) { return destination.startsWith(PREFIX); }

    @Override
    public boolean allows(String destination) {
        var match = DESTINATION.matcher(destination);
        if (!match.matches()) return false;
        String workspace = match.group(1), channel = match.group(2);
        var actor = humans.currentActor(workspace);
        if (WORK.equals(channel)) return actor.hasRole("agent", "coordinator");
        List<LiveChannelGuard> owners = guards.orderedStream().filter(guard -> guard.supports(channel)).toList();
        return owners.size() == 1 && owners.get(0).allows(workspace, channel);
    }
}
