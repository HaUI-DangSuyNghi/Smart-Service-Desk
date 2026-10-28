package vn.haui.dx.hpdi.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.haui.dx.core.identity.api.FreshAdminAuthorization;
import vn.haui.dx.core.common.CoreException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="FILE_TEST_JDBC_URL", matches=".+/file_storage")
class WorkSettingsIntegrationTest {
    private static final String PATH = "/api/hpdi/v1/settings/work";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "https://settings.test");
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:9/jwks");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockBean FreshAdminAuthorization authorization;
    private ObjectNode current() throws Exception {
        return (ObjectNode) json.readTree(mvc.perform(get(PATH).with(jwt().jwt(token -> token.subject("admin"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private ObjectNode request() throws Exception {
        ObjectNode result = current(); result.remove(List.of("updatedAt", "updatedBy")); return result;
    }
    private int replace(ObjectNode body) throws Exception {
        return mvc.perform(put(PATH).with(jwt().jwt(token -> token.subject("admin")))
            .contentType("application/json").content(body.toString())).andReturn().getResponse().getStatus();
    }
    @Test void getPutValidationCasConcurrencyAndRollback() throws Exception {
        when(authorization.requirePlatformAdmin()).thenReturn("admin");
        ObjectNode before = current();
        assertEquals(510, before.at("/sla/targets/LOW/startWithinMinutes").asInt());
        assertEquals(2550, before.at("/sla/targets/LOW/resolveWithinMinutes").asInt());
        assertEquals(1020, before.at("/sla/targets/MEDIUM/resolveWithinMinutes").asInt());
        ObjectNode change = request();
        assertEquals(200, replace(change)); assertEquals(409, replace(change));
        assertEquals("admin", current().path("updatedBy").asText());
        var invalid = request(); invalid.withObject("assignment").put("unknown", true);
        assertEquals(400, replace(invalid));
        invalid = request(); invalid.put("updatedBy", "forged"); assertEquals(400, replace(invalid));
        invalid = request(); invalid.withObject("estimate").put("outlierMaxMinutes", 1); assertEquals(400, replace(invalid));
        invalid = request(); invalid.withArray("shifts").add(invalid.withArray("shifts").get(0).deepCopy());
        assertEquals(400, replace(invalid));
        ObjectNode concurrent = request();
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); return replace(concurrent); });
            var second = executor.submit(() -> { start.await(); return replace(concurrent); });
            start.countDown();
            assertEquals(List.of(200, 409), java.util.stream.Stream.of(first.get(), second.get()).sorted().toList());
        } finally { executor.shutdownNow(); }
        ObjectNode rollback = current(); long auditCount = auditCount();
        jdbc.execute("create function settings_audit_fail() returns trigger language plpgsql as $$ begin if NEW.action='hpdi.work-settings.update' then raise exception 'proof'; end if; return NEW; end $$");
        jdbc.execute("create trigger settings_audit_fail before insert on audit_event for each row execute function settings_audit_fail()");
        try { assertThrows(Exception.class, () -> replace(request())); }
        finally { jdbc.execute("drop trigger settings_audit_fail on audit_event"); jdbc.execute("drop function settings_audit_fail()"); }
        assertEquals(rollback, current()); assertEquals(auditCount, auditCount());
        assertEquals(200, replace(request()));
        jdbc.update("update hpdi_work_settings set version=9223372036854775806 where id=1");
        assertEquals(200, replace(request())); assertEquals(Long.MAX_VALUE, current().path("version").asLong());
        assertEquals(409, replace(request()));
        jdbc.update("update hpdi_work_settings set version=1 where id=1");
    }
    private long auditCount() { return jdbc.queryForObject("select count(*) from audit_event where action='hpdi.work-settings.update'", Long.class); }
    @Test void authAndReservedScopeFailClosed() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        for (CoreException denial : List.of(CoreException.forbidden("revoked"), CoreException.unavailable("outage"))) {
            doThrow(denial).when(authorization).requirePlatformAdmin();
            mvc.perform(get(PATH).with(jwt())).andExpect(status().is(denial.code().status()));
            mvc.perform(put(PATH).with(jwt()).contentType("application/json").content("{}"))
                .andExpect(status().is(denial.code().status()));
        }
    }
}
