package vn.haui.dx.apps.servicedesk;

import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.human.api.Notifications;
import vn.haui.dx.hpdi.human.api.Notifications.Severity;

/**
 * Thông báo (chuông + email) cho từng lần đổi trạng thái của ticket: ai nhận, có email không.
 * Người tự làm thao tác không nhận (Notifications tự loại).
 */
@Component
public class TicketNotices {
    private record Ticket(UUID id, String workspace, String title, String creator) {
        String code() { String text = id.toString(); return "#" + text.substring(text.length() - 6); }
        String link() { return "/servicedesk/workspaces/" + workspace + "/tickets/" + id; }
    }

    private final JdbcTemplate jdbc;
    private final ObjectProvider<Notifications> notifications;
    public TicketNotices(JdbcTemplate jdbc, ObjectProvider<Notifications> notifications) {
        this.jdbc = jdbc; this.notifications = notifications;
    }

    /** Người gửi tạo ticket: điều phối viên (một thông báo duy nhất; không báo thêm lúc AI phân loại xong). */
    public void created(String workspace, UUID ticketId) {
        ticket(workspace, ticketId, null).ifPresent(t ->
            send(t, "TICKET_CREATED", Severity.INFO, "Yêu cầu mới " + t.code(), Set.of(), true, true));
    }

    /** AI cần người gửi bổ sung thông tin. */
    public void needsInformation(String workspace, UUID ticketId) {
        ticket(workspace, ticketId, null).ifPresent(t ->
            send(t, "TICKET_NEEDS_INFO", Severity.WARNING, "Cần bổ sung thông tin cho " + t.code(), Set.of(t.creator()), false, true));
    }

    /** Duyệt và giao / giao lại: người được giao, người giữ trước (nếu có), người gửi. */
    public void assigned(String workspace, String processId, String assignee, String previous) {
        ticket(workspace, null, processId).ifPresent(t -> {
            send(t, "TICKET_ASSIGNED", Severity.INFO, "Bạn được giao " + t.code(), Set.of(assignee), false, true);
            if (previous != null && !previous.equals(assignee))
                send(t, "TICKET_REASSIGNED", Severity.INFO, t.code() + " đã được giao cho người khác", Set.of(previous), false, false);
            if (previous == null)
                send(t, "TICKET_ACCEPTED", Severity.INFO, "Yêu cầu " + t.code() + " đã được tiếp nhận và giao xử lý", Set.of(t.creator()), false, true);
        });
    }

    /** Kỹ thuật viên bắt đầu xử lý: người gửi (chỉ trong app). */
    public void started(String workspace, String processId) {
        ticket(workspace, null, processId).ifPresent(t ->
            send(t, "TICKET_STARTED", Severity.INFO, "Yêu cầu " + t.code() + " đang được xử lý", Set.of(t.creator()), false, false));
    }

    /** Tạm dừng chờ người gửi trả lời: người gửi. */
    public void waitingRequester(String workspace, String processId) {
        ticket(workspace, null, processId).ifPresent(t ->
            send(t, "TICKET_WAITING_REQUESTER", Severity.WARNING, "Kỹ thuật viên đang chờ bạn phản hồi " + t.code(),
                Set.of(t.creator()), false, true));
    }

    /** Kỹ thuật viên trả việc: điều phối viên giao lại. */
    public void released(String workspace, String processId) {
        ticket(workspace, null, processId).ifPresent(t ->
            send(t, "TICKET_RELEASED", Severity.WARNING, t.code() + " bị trả lại, cần giao lại", Set.of(), true, true));
    }

    /** Báo xong: người gửi xác nhận kết quả. */
    public void resolved(String workspace, String processId) {
        ticket(workspace, null, processId).ifPresent(t ->
            send(t, "TICKET_RESOLVED", Severity.INFO, "Yêu cầu " + t.code() + " đã xử lý xong, mời bạn xác nhận",
                Set.of(t.creator()), false, true));
    }

    /** Người gửi xác nhận: đã ổn báo người xử lý; chưa xong báo người xử lý và điều phối viên (có email). */
    public void confirmed(String workspace, String processId, boolean accepted) {
        ticket(workspace, null, processId).ifPresent(t -> {
            var handler = lastAssignee(workspace, processId);
            var subjects = handler == null ? Set.<String>of() : Set.of(handler);
            if (accepted) send(t, "TICKET_CONFIRMED", Severity.INFO, "Người gửi đã xác nhận " + t.code() + " đã ổn", subjects, false, true);
            else send(t, "TICKET_REOPENED", Severity.WARNING, "Người gửi báo " + t.code() + " chưa xong", subjects, true, true);
        });
    }

    /** Hết hạn xác nhận, hệ thống tự đóng: người gửi. */
    public void autoClosed(String workspace, String processId) {
        ticket(workspace, null, processId).ifPresent(t ->
            send(t, "TICKET_AUTO_CLOSED", Severity.INFO, "Yêu cầu " + t.code() + " đã tự đóng", Set.of(t.creator()), false, true));
    }

    /** Bình luận mới: người gửi, người xử lý, người đã bình luận; nội dung bình luận làm phần thân (có email). */
    public void commented(String workspace, UUID ticketId, String comment, Set<String> subjects) {
        ticket(workspace, ticketId, null).ifPresent(t ->
            send(t, "TICKET_COMMENTED", Severity.INFO, "Bình luận mới trên " + t.code() + ": " + t.title(), comment,
                Set.copyOf(subjects), false, true));
    }

    /** Người giữ việc xử lý gần nhất của ticket (trước khi giao lại / đóng). */
    public String lastAssignee(String workspace, String processId) {
        return jdbc.query("select subject from servicedesk_assignment_event where workspace=? and process_id=?"
                + " order by assigned_at desc limit 1", (row, index) -> row.getString(1), workspace, processId)
            .stream().findFirst().orElse(null);
    }

    private void send(Ticket t, String type, Severity severity, String title, Set<String> subjects, boolean coordinators, boolean email) {
        send(t, type, severity, title, t.title(), subjects, coordinators, email);
    }

    private void send(Ticket t, String type, Severity severity, String title, String body, Set<String> subjects,
                      boolean coordinators, boolean email) {
        notifications.getObject().notify(new Notifications.Notice(t.workspace(), type, severity, title, body, t.link(),
            null, t.id().toString(), null, subjects, coordinators, email));
    }

    private java.util.Optional<Ticket> ticket(String workspace, UUID id, String processId) {
        return jdbc.query("select id,workspace,title,creator from servicedesk_ticket where workspace=? and "
                + (id != null ? "id=?" : "process_instance_id=?"),
            (row, index) -> new Ticket(row.getObject("id", UUID.class), row.getString("workspace"), row.getString("title"),
                row.getString("creator")), workspace, id != null ? id : processId).stream().findFirst();
    }
}
