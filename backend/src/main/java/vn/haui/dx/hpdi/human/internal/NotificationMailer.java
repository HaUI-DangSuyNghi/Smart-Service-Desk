package vn.haui.dx.hpdi.human.internal;

import jakarta.mail.internet.InternetAddress;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import vn.haui.dx.core.identity.api.WorkspaceDirectory;

/**
 * Gửi email cho các dòng người nhận đang PENDING (hàng đợi trong hpdi_notification_recipient).
 * Lỗi thì thử lại giãn cách (2, 4, 8, 16 phút), quá 5 lần thì FAILED; không có email thì SKIPPED.
 * MAIL_HOST trống thì không gửi gì (thông báo trong app vẫn có).
 */
@Component
public class NotificationMailer {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(NotificationMailer.class);
    private static final int MAX_ATTEMPTS = 5;

    private final JdbcTemplate jdbc;
    private final WorkspaceDirectory directory;
    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String from;
    private final String fromName;
    private final String appUrl;

    public NotificationMailer(JdbcTemplate jdbc, WorkspaceDirectory directory, ObjectProvider<JavaMailSender> sender,
            @Value("${spring.mail.host:}") String host, @Value("${spring.mail.username:}") String from,
            @Value("${dx.notifications.mail-from-name:Smart Desk App}") String fromName,
            @Value("${dx.notifications.app-url:http://localhost:5173}") String appUrl) {
        this.jdbc = jdbc; this.directory = directory; this.sender = sender;
        this.host = host; this.from = from; this.fromName = fromName;
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    }

    @Scheduled(fixedDelayString = "${dx.notifications.mail-poll-ms:10000}", initialDelayString = "${dx.notifications.mail-poll-ms:10000}")
    public void run() {
        var mail = sender.getIfAvailable();
        if (mail == null || host.isBlank() || from.isBlank()) return;
        // Giữ chỗ lô này 5 phút (máy chủ khác không gửi trùng); gửi xong thì cập nhật trạng thái thật
        var batch = jdbc.queryForList("update hpdi_notification_recipient r set email_next_at=clock_timestamp()+interval '5 minutes'"
            + " from (select notification_id,subject from hpdi_notification_recipient where email_status='PENDING'"
            + " and email_next_at<=clock_timestamp() order by email_next_at limit 20 for update skip locked) c"
            + " where r.notification_id=c.notification_id and r.subject=c.subject"
            + " returning r.notification_id,r.subject,r.email_attempts");
        if (batch.isEmpty()) return;
        var emails = directory.emails(batch.stream().map(row -> (String) row.get("subject")).toList());
        for (var row : batch) {
            UUID id = (UUID) row.get("notification_id");
            String subject = (String) row.get("subject");
            String address = emails.get(subject);
            if (address == null) {
                update(id, subject, "SKIPPED", (Integer) row.get("email_attempts"), "No email address");
                continue;
            }
            try {
                send(mail, address, jdbc.queryForMap("select * from hpdi_notification where id=?", id));
                jdbc.update("update hpdi_notification_recipient set email_status='SENT',emailed_at=clock_timestamp(),email_error=null"
                    + " where notification_id=? and subject=?", id, subject);
            } catch (Exception failure) {
                LOG.warn("Notification email to {} failed: {}", subject, failure.getMessage());
                int attempts = (Integer) row.get("email_attempts") + 1;
                update(id, subject, attempts >= MAX_ATTEMPTS ? "FAILED" : "PENDING", attempts, failure.getMessage());
            }
        }
    }

    private void update(UUID id, String subject, String status, int attempts, String error) {
        jdbc.update("update hpdi_notification_recipient set email_status=?,email_attempts=?,email_error=?,"
            + "email_next_at=clock_timestamp()+make_interval(mins => ?) where notification_id=? and subject=?",
            status, attempts, error == null ? null : error.substring(0, Math.min(error.length(), 300)),
            (int) Math.pow(2, Math.max(1, attempts)), id, subject);
    }

    private void send(JavaMailSender mail, String address, Map<String, Object> notice) throws Exception {
        var message = mail.createMimeMessage();
        var helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(new InternetAddress(from, fromName, "UTF-8"));
        helper.setTo(address);
        String title = (String) notice.get("title");
        helper.setSubject("[" + fromName + "] " + title);
        String link = (String) notice.get("link");
        helper.setText(html(title, (String) notice.get("body"), appUrl + (link == null ? "/notifications" : link)), true);
        mail.send(message);
    }

    /** Email HTML đơn giản: tiêu đề, nội dung ngắn, nút mở trong web. */
    static String html(String title, String body, String url) {
        String safeUrl = HtmlUtils.htmlEscape(url, "UTF-8");
        return "<div style=\"font-family:Segoe UI,Arial,sans-serif;max-width:560px;margin:0 auto;color:#1f2328\">"
            + "<h2 style=\"font-size:18px;margin:0 0 12px\">" + HtmlUtils.htmlEscape(title, "UTF-8") + "</h2>"
            + (body == null || body.isBlank() ? "" : "<p style=\"font-size:14px;line-height:1.6;margin:0 0 20px;white-space:pre-line\">" + HtmlUtils.htmlEscape(body, "UTF-8") + "</p>")
            + "<a href=\"" + safeUrl + "\" style=\"display:inline-block;background:#1961ed;color:#fff;text-decoration:none;"
            + "padding:10px 18px;border-radius:6px;font-size:14px\">Mở trong Smart Desk</a>"
            + "<p style=\"font-size:12px;color:#6e7781;margin:24px 0 0\">Email tự động từ Smart Desk App, vui lòng không trả lời.</p></div>";
    }
}
