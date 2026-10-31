package vn.haui.dx.hpdi.human;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.haui.dx.hpdi.human.api.Notifications;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="FILE_TEST_JDBC_URL", matches=".+/file_storage")
class NotificationIntegrationTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Notifications notifications;
    private static final String BASE = "/api/hpdi/v1/workspaces/lab-a/notifications";

    private org.springframework.test.web.servlet.request.RequestPostProcessor as(String subject, String role) {
        return jwt().jwt(token -> token.subject(subject).claim("groups", List.of("/workspaces/lab-a/" + role)));
    }

    @Test void recipientsSkipTheActorQueueEmailAndOnlyReadTheirOwn() throws Exception {
        String actor = "notice-actor-" + UUID.randomUUID(), requester = "notice-requester-" + UUID.randomUUID();
        notifications.notify(new Notifications.Notice("lab-a", "TICKET_RESOLVED", Notifications.Severity.INFO,
            "Yêu cầu #abc123 đã xử lý xong", "Máy 15 không lên nguồn", "/servicedesk/tickets/x", null, "x", actor,
            Set.of(actor, requester), false, true));
        // Người làm thao tác không nhận; người nhận có email chờ gửi
        assertEquals(0, jdbc.queryForObject("select count(*) from hpdi_notification_recipient where subject=?", Integer.class, actor));
        assertEquals("PENDING", jdbc.queryForObject("select email_status from hpdi_notification_recipient where subject=?", String.class, requester));

        mvc.perform(get(BASE).with(as(requester, "requester"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.unread").value(1)).andExpect(jsonPath("$.items[0].type").value("TICKET_RESOLVED"))
            .andExpect(jsonPath("$.items[0].link").value("/servicedesk/tickets/x"));
        String id = jdbc.queryForObject("select notification_id::text from hpdi_notification_recipient where subject=?", String.class, requester);
        // Người khác không đánh dấu được thông báo không phải của mình
        mvc.perform(post(BASE + "/" + id + "/read").with(as(actor, "agent"))).andExpect(status().isNotFound());
        mvc.perform(post(BASE + "/" + id + "/read").with(as(requester, "requester"))).andExpect(status().isNoContent());
        mvc.perform(post(BASE + "/" + id + "/read").with(as(requester, "requester"))).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "?unreadOnly=true").with(as(requester, "requester"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.unread").value(0)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get(BASE + "?size=99").with(as(requester, "requester"))).andExpect(status().isBadRequest());
    }
}
