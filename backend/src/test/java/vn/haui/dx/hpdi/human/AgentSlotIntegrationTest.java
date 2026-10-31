package vn.haui.dx.hpdi.human;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
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
import vn.haui.dx.core.identity.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="FILE_TEST_JDBC_URL", matches=".+/file_storage")
class AgentSlotIntegrationTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkspaceDirectory directory;
    @Autowired ObjectMapper json;
    private static final String BASE = "/api/hpdi/v1/workspaces/lab-a/schedules";
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

    private org.springframework.test.web.servlet.request.RequestPostProcessor as(String subject, String role) {
        return jwt().jwt(token -> token.subject(subject).claim("groups", List.of("/workspaces/lab-a/" + role)));
    }
    private String[] agents() {
        String first = "slot-a-" + UUID.randomUUID(), second = "slot-b-" + UUID.randomUUID();
        when(directory.members("lab-a")).thenReturn(List.of(
            new DirectoryMember(first, first, null, Set.of(WorkspaceRole.AGENT)),
            new DirectoryMember(second, second, null, Set.of(WorkspaceRole.AGENT)),
            new DirectoryMember("coordinator", "coordinator", null, Set.of(WorkspaceRole.COORDINATOR))));
        return new String[]{first, second};
    }
    /** Ngày làm việc đầu tiên sau hôm nay (bỏ T7, CN, ngày lễ trong Cấu hình): kỹ thuật viên luôn còn hạn đăng ký. */
    private LocalDate nextWeekday() {
        var holidays = Set.copyOf(jdbc.queryForList("select h->>'date' from hpdi_work_settings,"
            + " jsonb_array_elements(coalesce(payload->'schedule'->'holidays','[]'::jsonb)) h", String.class));
        var date = today.plusDays(1);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY
                || holidays.contains(date.toString())) date = date.plusDays(1);
        return date;
    }
    private LocalDate nextSaturday() {
        var date = today.plusDays(1);
        while (date.getDayOfWeek() != DayOfWeek.SATURDAY) date = date.plusDays(1);
        return date;
    }
    private String body(LocalDate date, String shift) { return "{\"date\":\"" + date + "\",\"shift\":\"" + shift + "\"}"; }

    @Test void agentRegistersOwnSlotsBeforeTheDayAndCoordinatorRegistersOnBehalf() throws Exception {
        var subjects = agents();
        String agent = subjects[0], other = subjects[1];
        var day = nextWeekday();

        mvc.perform(get(BASE + "/policy").with(as(agent, "agent"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.cutoff").value(org.hamcrest.Matchers.startsWith("07:00")))
            .andExpect(jsonPath("$.weekendRegistration").value(false));

        var created = json.readTree(mvc.perform(post(BASE + "/me/slots").with(as(agent, "agent"))
                .contentType("application/json").content(body(day, "Morning")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"))
            .andReturn().getResponse().getContentAsString());
        // Trùng ô, ô của người khác, ngày đã qua, T7 khi chưa bật, ca không có trong cấu hình
        mvc.perform(post(BASE + "/me/slots").with(as(agent, "agent")).contentType("application/json").content(body(day, "Morning")))
            .andExpect(status().isConflict());
        mvc.perform(post(BASE + "/" + other + "/slots").with(as(agent, "agent")).contentType("application/json").content(body(day, "Morning")))
            .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/me/slots").with(as(agent, "agent")).contentType("application/json").content(body(today.minusDays(1), "Morning")))
            .andExpect(status().isConflict());
        mvc.perform(post(BASE + "/me/slots").with(as(agent, "agent")).contentType("application/json").content(body(nextSaturday(), "Morning")))
            .andExpect(status().isConflict());
        mvc.perform(post(BASE + "/me/slots").with(as(agent, "agent")).contentType("application/json").content(body(day, "Night")))
            .andExpect(status().isBadRequest());

        // Điều phối viên đăng ký hộ (duyệt luôn), nhưng không đăng ký ca đã kết thúc
        mvc.perform(post(BASE + "/" + other + "/slots").with(as("coordinator", "coordinator"))
                .contentType("application/json").content(body(day, "Afternoon")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.registeredBy").value("coordinator"));
        mvc.perform(post(BASE + "/" + other + "/slots").with(as("coordinator", "coordinator"))
                .contentType("application/json").content(body(today.minusDays(1), "Afternoon")))
            .andExpect(status().isConflict());

        // Kỹ thuật viên chỉ thấy ô của mình; điều phối viên thấy cả hai
        String range = "/slots?from=" + day + "&to=" + day;
        var mine = json.readTree(mvc.perform(get(BASE + range).with(as(agent, "agent"))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        assertTrue(mine.findValuesAsText("subject").stream().allMatch(agent::equals));
        var all = json.readTree(mvc.perform(get(BASE + range).with(as("coordinator", "coordinator"))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        assertTrue(all.findValuesAsText("subject").containsAll(List.of(agent, other)));
        mvc.perform(get(BASE + "/slots?from=" + day + "&to=" + day.plusDays(100)).with(as(agent, "agent")))
            .andExpect(status().isBadRequest());

        // Duyệt ô đã duyệt: xung đột; huỷ ô của mình trước hạn: được; ghi audit
        String id = created.path("id").asText();
        mvc.perform(post(BASE + "/slots/" + id + "/approve").with(as("coordinator", "coordinator"))).andExpect(status().isConflict());
        mvc.perform(post(BASE + "/slots/" + id + "/approve").with(as(agent, "agent"))).andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/me/slots/" + id).with(as(agent, "agent"))).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("select count(*) from hpdi_agent_slot where id=?::uuid", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where workspace='lab-a' and action='human.schedule.slot.cancelled' and resource=?",
            Integer.class, agent));
    }

    @Test void officeHoursAgentsSkipWeekdayRegistrationAndOnlyCoordinatorsChangeMode() throws Exception {
        var subjects = agents();
        String office = subjects[0];
        // Ô ngày thường đăng ký trước khi chuyển sang hành chính sẽ bị bỏ (đã tự trực)
        mvc.perform(post(BASE + "/me/slots").with(as(office, "agent")).contentType("application/json")
                .content(body(nextWeekday(), "Afternoon")))
            .andExpect(status().isCreated());
        // Không đổi hộ người khác; tự chọn của mình thì được
        mvc.perform(put(BASE + "/" + subjects[1] + "/work-mode").with(as(office, "agent"))
                .contentType("application/json").content("{\"mode\":\"OFFICE\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/me/work-mode").with(as(office, "agent"))
                .contentType("application/json").content("{\"mode\":\"OFFICE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("OFFICE"));
        assertEquals(0, jdbc.queryForObject("select count(*) from hpdi_agent_slot where workspace='lab-a' and subject=?",
            Integer.class, office));
        mvc.perform(put(BASE + "/" + office + "/work-mode").with(as("coordinator", "coordinator"))
                .contentType("application/json").content("{\"mode\":\"PART_TIME\"}"))
            .andExpect(status().isBadRequest());
        // Kỹ thuật viên thấy hình thức của mình; ngày thường không cần (và không được) đăng ký
        mvc.perform(get(BASE + "/work-modes").with(as(office, "agent"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].mode").value("OFFICE"));
        mvc.perform(post(BASE + "/me/slots").with(as(office, "agent")).contentType("application/json")
                .content(body(nextWeekday(), "Morning")))
            .andExpect(status().isConflict());
    }
}
