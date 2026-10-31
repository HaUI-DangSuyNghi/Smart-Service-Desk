package vn.haui.dx.hpdi.human.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.core.identity.api.WorkspaceDirectory;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.*;

@Service
public class NotificationCenter implements Notifications {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(NotificationCenter.class);
    // Tên tiếng Việt của cảnh báo (WorkAlerts.Type) khi chép sang chuông
    private static final Map<String, String> ALERT_TITLES = Map.ofEntries(
        Map.entry("START_OVERDUE", "Quá hạn bắt đầu xử lý"), Map.entry("SLA_WARNING", "Sắp quá hạn xử lý"),
        Map.entry("SLA_BREACHED", "Đã quá hạn xử lý"), Map.entry("PENDING_TOO_LONG", "Việc đang chờ quá lâu"),
        Map.entry("CRITICAL_NEW", "Có yêu cầu khẩn cấp mới"), Map.entry("NO_ONE_ON_DUTY", "Không ai đang trực"),
        Map.entry("ASSIGNEE_OFF_DUTY", "Người xử lý đang nghỉ"), Map.entry("CLASSIFICATION_CHANGED", "Phân loại đã thay đổi"),
        Map.entry("REOPENED", "Yêu cầu bị mở lại"), Map.entry("REOPEN_UNCLAIMED", "Yêu cầu mở lại chưa ai nhận"),
        Map.entry("CRITICAL_UNREVIEWED", "Yêu cầu khẩn cấp chưa được duyệt"));

    private final JdbcTemplate jdbc;
    private final HumanContext humans;
    private final WorkspaceDirectory directory;
    public NotificationCenter(JdbcTemplate jdbc, HumanContext humans, WorkspaceDirectory directory) {
        this.jdbc = jdbc; this.humans = humans; this.directory = directory;
    }
    private LiveUpdates live;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setLive(LiveUpdates live) { this.live = live; }

    @Override
    @Transactional
    public void notify(Notice notice) {
        if (notice == null || !text(notice.workspace(), 100) || notice.type() == null || !notice.type().matches("[A-Z][A-Z0-9_]{1,59}")
                || notice.severity() == null || !text(notice.title(), 2000) || notice.link() != null && !notice.link().startsWith("/"))
            throw CapabilityException.validation("Invalid notification");
        String actor = notice.actor() != null ? notice.actor() : currentSubject(notice.workspace());
        var recipients = new LinkedHashSet<String>();
        notice.subjects().stream().filter(subject -> text(subject, 100)).forEach(recipients::add);
        if (notice.coordinators()) recipients.addAll(coordinators(notice.workspace()));
        // Người tự làm thao tác không nhận thông báo của chính thao tác đó
        if (actor != null) recipients.remove(actor);
        if (recipients.isEmpty()) return;
        insert(notice.workspace(), notice.type(), "NOTICE", notice.severity().name(), notice.title(), notice.body(), notice.link(),
            notice.processKey(), notice.businessKey(), actor, recipients, notice.email());
    }

    /** Bản sao cảnh báo (đã phát cho người nhận) để chuông chứa cả cảnh báo; chỉ cảnh báo nghiêm trọng mới gửi email. */
    void mirrorAlert(String workspace, Map<String, Object> event, Set<String> recipients) {
        if (recipients.isEmpty()) return;
        String type = (String) event.get("type");
        String severity = (String) event.get("severity");
        insert(workspace, type, "ALERT", severity, ALERT_TITLES.getOrDefault(type, "Cảnh báo"), (String) event.get("label"), null,
            (String) event.get("process_key"), (String) event.get("business_key"), null, recipients, "CRITICAL".equals(severity));
    }

    private void insert(String workspace, String type, String category, String severity, String title, String body, String link,
            String processKey, String businessKey, String actor, Collection<String> recipients, boolean email) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into hpdi_notification(id,workspace,type,category,severity,title,body,link,process_key,business_key,actor)"
            + " values(?,?,?,?,?,?,?,?,?,?,?)", id, workspace, type, category, severity, cut(title, 300), cut(body, 1000), cut(link, 500),
            cut(processKey, 100), cut(businessKey, 200), cut(actor, 100));
        for (String subject : recipients)
            jdbc.update("insert into hpdi_notification_recipient(workspace,notification_id,subject,email_status,email_next_at)"
                + " values(?,?,?,?,case when ? then clock_timestamp() end) on conflict do nothing",
                workspace, id, subject, email ? "PENDING" : "NONE", email);
        // Chuông của người nhận đang mở tải lại ngay (sau commit)
        if (live != null) for (String subject : recipients) live.toUser(workspace, subject, "NOTIFICATION", "notification", id.toString());
    }

    @Override
    @Transactional(readOnly = true)
    public Inbox list(String workspace, boolean unreadOnly, int page, int size) {
        if (page < 0 || page > 10_000 || size < 1 || size > 50) throw CapabilityException.validation("Invalid page");
        String subject = humans.currentActor(workspace).subject();
        String from = " from hpdi_notification_recipient r join hpdi_notification n on n.id=r.notification_id"
            + " where r.workspace=? and r.subject=?";
        long unread = jdbc.queryForObject("select count(*)" + from + " and r.read_at is null", Long.class, workspace, subject);
        if (unreadOnly) from += " and r.read_at is null";
        long total = jdbc.queryForObject("select count(*)" + from, Long.class, workspace, subject);
        var items = jdbc.query("select n.*,r.read_at" + from + " order by n.created_at desc,n.id desc limit ? offset ?",
            NotificationCenter::item, workspace, subject, size, (long) page * size);
        return new Inbox(items, page, size, total, unread);
    }

    @Override
    @Transactional
    public void read(String workspace, UUID id) {
        String subject = humans.currentActor(workspace).subject();
        if (id == null || jdbc.queryForObject("select count(*) from hpdi_notification_recipient where workspace=? and notification_id=? and subject=?",
                Integer.class, workspace, id, subject) == 0) throw CapabilityException.notFound("Notification not found");
        jdbc.update("update hpdi_notification_recipient set read_at=clock_timestamp() where workspace=? and notification_id=? and subject=? and read_at is null",
            workspace, id, subject);
    }

    @Override
    @Transactional
    public void readAll(String workspace) {
        String subject = humans.currentActor(workspace).subject();
        jdbc.update("update hpdi_notification_recipient set read_at=clock_timestamp() where workspace=? and subject=? and read_at is null",
            workspace, subject);
    }

    /** Điều phối viên hiện tại của workspace; danh bạ không đọc được (worker không có quyền...) thì bỏ qua. */
    private Set<String> coordinators(String workspace) {
        try {
            return directory.members(workspace).stream()
                .filter(member -> member.roles().stream().anyMatch(role -> "coordinator".equals(role.value())))
                .map(member -> member.sub()).collect(java.util.stream.Collectors.toSet());
        } catch (RuntimeException failure) {
            LOG.warn("Cannot resolve coordinators of {} for notification: {}", workspace, failure.getMessage());
            return Set.of();
        }
    }

    private String currentSubject(String workspace) {
        try { return humans.currentActor(workspace).subject(); }
        catch (RuntimeException noActor) { return null; }
    }

    private static Item item(ResultSet row, int index) throws SQLException {
        var read = row.getTimestamp("read_at");
        return new Item(row.getObject("id", UUID.class), row.getString("type"), row.getString("category"), row.getString("severity"),
            row.getString("title"), row.getString("body"), row.getString("link"), row.getString("process_key"),
            row.getString("business_key"), row.getTimestamp("created_at").toInstant(), read == null ? null : read.toInstant());
    }

    private static boolean text(String value, int max) { return value != null && !value.isBlank() && value.length() <= max; }
    private static String cut(String value, int max) { return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…"; }
}
