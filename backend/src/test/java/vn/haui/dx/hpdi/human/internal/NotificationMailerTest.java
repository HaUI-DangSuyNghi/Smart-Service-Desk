package vn.haui.dx.hpdi.human.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationMailerTest {
    @Test void emailEscapesTicketTextAndKeepsTheLink() {
        String html = NotificationMailer.html("<b>Yêu cầu</b>", "Máy 15 & máy 16", "http://localhost:5173/servicedesk/tickets/x?a=1&b=2");
        // Tiêu đề, mô tả ticket do người dùng nhập: không được thành HTML trong email
        assertTrue(html.contains("&lt;b&gt;Yêu cầu&lt;/b&gt;"));
        assertFalse(html.contains("<b>Yêu cầu</b>"));
        assertTrue(html.contains("Máy 15 &amp; máy 16"));
        assertTrue(html.contains("href=\"http://localhost:5173/servicedesk/tickets/x?a=1&amp;b=2\""));
    }
}
