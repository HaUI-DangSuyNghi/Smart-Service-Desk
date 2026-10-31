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
import vn.haui.dx.core.identity.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="FILE_TEST_JDBC_URL", matches=".+/file_storage")
class AgentDutyIntegrationTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkspaceDirectory directory;
    private static final String BASE = "/api/hpdi/v1/workspaces/lab-a/duty";

    private org.springframework.test.web.servlet.request.RequestPostProcessor as(String subject, String role) {
        return jwt().jwt(token -> token.subject(subject).claim("groups", List.of("/workspaces/lab-a/" + role)));
    }
    private String agent() {
        String subject = "duty-" + UUID.randomUUID();
        when(directory.members("lab-a")).thenReturn(List.of(new DirectoryMember(subject, subject, null, Set.of(WorkspaceRole.AGENT)),
            new DirectoryMember("coordinator","coordinator",null,Set.of(WorkspaceRole.COORDINATOR))));
        return subject;
    }

    private String schedule(String subject) { return BASE.replace("/duty","/schedules/")+subject; }
    private String shiftChange(long version, boolean enabled) {
        var start=java.time.Instant.now().minusSeconds(60);
        return "{\"version\":"+version+",\"enabled\":"+enabled+",\"shifts\":[{\"startsAt\":\""+start
            +"\",\"endsAt\":\""+start.plusSeconds(3600)+"\"}]}";
    }

    @Test void scheduleOptInBoundariesManualOverrideAndSecurity() throws Exception {
        String subject=agent();
        mvc.perform(put(BASE+"/me").with(as(subject,"agent")).contentType("application/json").content("{\"onDuty\":true}"))
            .andExpect(status().isOk());
        mvc.perform(get(schedule(subject)+"/effective-duty").with(as(subject,"agent")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("LEGACY_MANUAL"));
        mvc.perform(put(schedule(subject)).with(as("coordinator","coordinator")).contentType("application/json").content(shiftChange(0,true)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.shifts[0].timezone").value("Asia/Ho_Chi_Minh"));
        mvc.perform(get(schedule("me")+"/effective-duty").with(as(subject,"agent")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.onDuty").value(true))
            .andExpect(jsonPath("$.scheduledDuty").value(true)).andExpect(jsonPath("$.shiftRemainingMinutes").isNumber());
        mvc.perform(put(BASE+"/me").with(as(subject,"agent")).contentType("application/json").content("{\"onDuty\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.effectiveDuty").value(false));
        mvc.perform(get(schedule(subject)+"/effective-duty").with(as(subject,"agent")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("MANUAL_OFF_DUTY"));
        mvc.perform(put(schedule(subject)).with(as(subject,"agent")).contentType("application/json").content(shiftChange(1,true)))
            .andExpect(status().isForbidden());
        mvc.perform(get(schedule(subject)).with(as("outsider","agent"))).andExpect(status().isForbidden());
        mvc.perform(get(schedule(subject)).with(as("requester","requester"))).andExpect(status().isForbidden());
        mvc.perform(get(schedule(subject).replace("lab-a","lab-b")).with(as(subject,"agent"))).andExpect(status().isForbidden());
        mvc.perform(put(schedule(subject)).with(as("coordinator","coordinator")).contentType("application/json").content(shiftChange(0,true)))
            .andExpect(status().isConflict());
        mvc.perform(put(schedule(subject)).with(as("coordinator","coordinator")).contentType("application/json")
            .content("{\"version\":1,\"enabled\":true,\"shifts\":[]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        assertEquals(1,jdbc.queryForObject("select count(*) from hpdi_agent_shift where workspace='lab-a' and subject=? and version=1",Integer.class,subject));
        mvc.perform(get(schedule(subject)+"/effective-duty").with(as(subject,"agent")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.scheduledDuty").value(false));
    }

    @Test void revokedCoordinatorAndAgentCannotUseStaleJwtScheduleAccess() throws Exception {
        String subject=agent();
        mvc.perform(put(schedule(subject)).with(as("coordinator","coordinator")).contentType("application/json").content(shiftChange(0,true)))
            .andExpect(status().isOk());
        when(directory.members("lab-a")).thenReturn(List.of(new DirectoryMember(subject,subject,null,Set.of(WorkspaceRole.AGENT))));
        mvc.perform(put(schedule(subject)).with(as("coordinator","coordinator")).contentType("application/json").content(shiftChange(1,true)))
            .andExpect(status().isForbidden());
        when(directory.members("lab-a")).thenReturn(List.of());
        mvc.perform(get(schedule(subject)).with(as(subject,"agent"))).andExpect(status().isForbidden());
        assertEquals(1L,jdbc.queryForObject("select version from hpdi_agent_schedule where workspace='lab-a' and subject=?",Long.class,subject));
    }

    @Test void twoCoordinatorsCannotOverwriteOneVersion() throws Exception {
        String subject=agent();
        var workers=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try {
            var results=new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int index=0;index<2;index++) results.add(workers.submit(() -> {
                assertTrue(start.await(10,java.util.concurrent.TimeUnit.SECONDS));
                return mvc.perform(put(schedule(subject)).with(as("coordinator","coordinator"))
                    .contentType("application/json").content(shiftChange(0,true))).andReturn().getResponse().getStatus();
            }));
            start.countDown();
            var codes=new java.util.ArrayList<Integer>();
            for (var result:results) codes.add(result.get(20,java.util.concurrent.TimeUnit.SECONDS));
            codes.sort(Integer::compareTo);
            assertEquals(List.of(200,409),codes);
            assertEquals(1,jdbc.queryForObject("select count(*) from audit_event where workspace='lab-a' and action='human.schedule.changed' and resource=?",Integer.class,subject));
        } finally { start.countDown(); workers.shutdownNow(); }
    }

    @Test void concurrentScheduleActivationCannotUndoManualOff() throws Exception {
        String subject=agent();
        var workers=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try {
            var schedule=workers.submit(() -> {
                assertTrue(start.await(10,java.util.concurrent.TimeUnit.SECONDS));
                return mvc.perform(put(schedule(subject)).with(as("coordinator","coordinator"))
                    .contentType("application/json").content(shiftChange(0,true))).andReturn().getResponse().getStatus();
            });
            var duty=workers.submit(() -> {
                assertTrue(start.await(10,java.util.concurrent.TimeUnit.SECONDS));
                return mvc.perform(put(BASE+"/me").with(as(subject,"agent"))
                    .contentType("application/json").content("{\"onDuty\":false}")).andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertEquals(200,schedule.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(200,duty.get(20,java.util.concurrent.TimeUnit.SECONDS));
            mvc.perform(get(schedule(subject)+"/effective-duty").with(as(subject,"agent")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.onDuty").value(false))
                .andExpect(jsonPath("$.scheduledDuty").value(true)).andExpect(jsonPath("$.scheduleVersion").value(1));
        } finally { start.countDown(); workers.shutdownNow(); }
    }

    @Test void scheduleAuditFailureRollsBackVersionAndShifts() throws Exception {
        String subject=agent();
        jdbc.execute("create function schedule_reject_audit() returns trigger language plpgsql as $$ begin if new.action='human.schedule.changed' then raise exception 'schedule rejection' using errcode='23514'; end if; return new; end $$");
        jdbc.execute("create trigger schedule_reject before insert on audit_event for each row execute function schedule_reject_audit()");
        try {
            mvc.perform(put(schedule(subject)).with(as("coordinator","coordinator")).contentType("application/json").content(shiftChange(0,true)))
                .andExpect(status().isConflict());
            assertEquals(0,jdbc.queryForObject("select count(*) from hpdi_agent_schedule where workspace='lab-a' and subject=?",Integer.class,subject));
            assertEquals(0,jdbc.queryForObject("select count(*) from hpdi_agent_shift where workspace='lab-a' and subject=?",Integer.class,subject));
        } finally { jdbc.execute("drop trigger schedule_reject on audit_event"); jdbc.execute("drop function schedule_reject_audit()"); }
    }

    @Test void persistedSelfOnlyDutyAndCoordinatorVisibilityAreWorkspaceIsolated() throws Exception {
        String subject = agent();
        mvc.perform(get(BASE).with(as(subject,"agent"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].onDuty").value(true));
        mvc.perform(put(BASE+"/me").with(as(subject,"agent")).contentType("application/json").content("{\"onDuty\":true,\"note\":\"Available\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.onDuty").value(true));
        assertTrue(jdbc.queryForObject("select on_duty from hpdi_agent_duty where workspace='lab-a' and subject=?",Boolean.class,subject));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where workspace='lab-a' and action='human.duty.changed' and resource=?",Integer.class,subject));
        mvc.perform(get(BASE).with(as("coordinator","coordinator"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].subject").value(subject));
        mvc.perform(get(BASE).with(as("another-agent","agent"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put(BASE+"/"+subject).with(as("coordinator","coordinator")).contentType("application/json").content("{\"onDuty\":false}"))
            .andExpect(status().isForbidden());
        mvc.perform(get(BASE.replace("lab-a","lab-b")).with(as(subject,"agent"))).andExpect(status().isForbidden());
        mvc.perform(put(BASE+"/me").with(as(subject,"agent")).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test void auditFailureRollsBackDutyMutation() throws Exception {
        String subject = agent();
        jdbc.execute("create function duty_reject_audit() returns trigger language plpgsql as $$ begin if new.action='human.duty.changed' then raise exception 'duty rejection' using errcode='23514'; end if; return new; end $$");
        jdbc.execute("create trigger duty_reject before insert on audit_event for each row execute function duty_reject_audit()");
        try {
            mvc.perform(put(BASE+"/me").with(as(subject,"agent"))
                .contentType("application/json").content("{\"onDuty\":true}")).andExpect(status().isConflict());
            assertEquals(0, jdbc.queryForObject("select count(*) from hpdi_agent_duty where workspace='lab-a' and subject=?",Integer.class,subject));
        } finally { jdbc.execute("drop trigger duty_reject on audit_event"); jdbc.execute("drop function duty_reject_audit()"); }
    }

    @Test void simultaneousDutyUpdatesKeepOneRowAndBothTransactionalAudits() throws Exception {
        String subject = agent();
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (boolean onDuty : List.of(true, false)) {
                results.add(workers.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                    return mvc.perform(put(BASE+"/me").with(as(subject,"agent")).contentType("application/json")
                        .content("{\"onDuty\":"+onDuty+"}")).andReturn().getResponse().getStatus();
                }));
            }
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            for (var result : results) assertEquals(200, result.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("select count(*) from hpdi_agent_duty where workspace='lab-a' and subject=?",Integer.class,subject));
            assertEquals(2, jdbc.queryForObject("select count(*) from audit_event where workspace='lab-a' and action='human.duty.changed' and resource=?",Integer.class,subject));
        } finally { start.countDown(); workers.shutdownNow(); }
    }
}
