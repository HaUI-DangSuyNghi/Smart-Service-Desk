package vn.haui.dx.apps.servicedesk;

import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.human.api.LiveChannelGuard;
import vn.haui.dx.hpdi.human.api.LiveUpdates;

/**
 * Báo màn hình đang mở khi ticket đổi: kênh "tickets" (danh sách, board của agent/coordinator),
 * kênh "tickets/{id}" (trang chi tiết, ai xem được ticket) và kênh riêng của người gửi.
 */
@Component
public class TicketRealtime implements LiveChannelGuard {
    private static final String LIST = "tickets";

    private final JdbcTemplate jdbc;
    private final HumanContext humans;
    private final ObjectProvider<LiveUpdates> live;
    // Lấy lười: TicketService gián tiếp phát sự kiện qua lớp này
    private final ObjectProvider<TicketService> tickets;

    public TicketRealtime(JdbcTemplate jdbc, HumanContext humans, ObjectProvider<LiveUpdates> live,
                          ObjectProvider<TicketService> tickets) {
        this.jdbc = jdbc; this.humans = humans; this.live = live; this.tickets = tickets;
    }

    /** Trạng thái, phân loại, AI, thông tin bổ sung... đổi. */
    public void changed(String workspace, UUID ticketId) {
        creator(workspace, ticketId).ifPresent(creator -> send(workspace, ticketId, creator));
    }

    /** Từ hook của quy trình (chỉ biết process instance). */
    public void changedByProcess(String workspace, String processId) {
        jdbc.query("select id,creator from servicedesk_ticket where workspace=? and process_instance_id=?",
                (row, index) -> new Object[] {row.getObject("id", UUID.class), row.getString("creator")}, workspace, processId)
            .stream().findFirst().ifPresent(row -> send(workspace, (UUID) row[0], (String) row[1]));
    }

    /** Bình luận mới: chỉ trang chi tiết cần tải lại. */
    public void commented(String workspace, UUID ticketId) {
        var updates = live.getIfAvailable();
        if (updates != null)
            updates.toChannel(workspace, LIST + "/" + ticketId, "TICKET_COMMENTED", "ticket", ticketId.toString());
    }

    // Tạo ticket và người gửi bổ sung thông tin đều phát sự kiện này
    @EventListener
    void contextChanged(TicketContextChanged event) {
        changed(event.workspace(), event.ticketId());
    }

    @Override
    public boolean supports(String channel) { return channel.equals(LIST) || channel.startsWith(LIST + "/"); }

    @Override
    public boolean allows(String workspace, String channel) {
        if (channel.equals(LIST)) return humans.currentActor(workspace).hasRole("agent", "coordinator");
        // Cùng quyền xem ticket: người gửi chỉ ticket của mình, không thấy thì ném lỗi = từ chối
        tickets.getObject().get(workspace, UUID.fromString(channel.substring(LIST.length() + 1)));
        return true;
    }

    private void send(String workspace, UUID ticketId, String creator) {
        var updates = live.getIfAvailable();
        if (updates == null) return;
        String id = ticketId.toString();
        updates.toChannel(workspace, LIST, "TICKET_CHANGED", "ticket", id);
        updates.toChannel(workspace, LIST + "/" + id, "TICKET_CHANGED", "ticket", id);
        updates.toUser(workspace, creator, "TICKET_CHANGED", "ticket", id);
    }

    private java.util.Optional<String> creator(String workspace, UUID ticketId) {
        return jdbc.query("select creator from servicedesk_ticket where workspace=? and id=?",
            (row, index) -> row.getString(1), workspace, ticketId).stream().findFirst();
    }
}
