package vn.haui.dx.hpdi.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.haui.dx.core.identity.api.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"serviceDesk.intake.new-tickets=true","serviceDesk.intake.true-wait=true",
    "serviceDesk.intake.poll-ms=3600000","service-desk.background.scheduled=false","serviceDesk.confirmation.scheduled=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="FILE_TEST_JDBC_URL",matches=".+/file_storage")
class RequestLifecycleIntegrationTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("FILE_TEST_JDBC_URL"));
        p.add("spring.datasource.username",()->"workflow");
        p.add("spring.datasource.password",()->System.getenv("FILE_TEST_DB_PASSWORD"));
        p.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",()->"http://127.0.0.1:1/jwks");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired vn.haui.dx.apps.servicedesk.clarification.TicketContextStore contexts;
    @Autowired vn.haui.dx.apps.servicedesk.intelligence.context.ContextSnapshotAssembler snapshots;
    @MockBean PermissionGuard permissions;
    @MockBean WorkspaceDirectory directory;
    @MockBean LiveUpdates live;
    @SpyBean ActivityJournal journal;
    @BeforeEach void directory() {
        when(directory.members(anyString())).thenReturn(List.of(new DirectoryMember("request-coordinator","coordinator",null,
            Set.of(WorkspaceRole.COORDINATOR))));
        when(directory.rolePermissions(anyString())).thenReturn(Map.of("coordinator",List.of(
            new DirectoryPermission("task:handle","Handle work"),new DirectoryPermission("task:assign","Assign work"))));
    }
    private RequestPostProcessor as(String owner,String role) {
        return jwt().jwt(t -> t.subject(owner).claim("groups",List.of("/workspaces/lab-a/"+role)));
    }
    private JsonNode body(ResultActions action) throws Exception { return json.readTree(action.andReturn().getResponse().getContentAsByteArray()); }
    private JsonNode create(boolean ticket,String owner) throws Exception {
        return body(mvc.perform(post(ticket ? "/api/apps/servicedesk/v1/workspaces/lab-a/tickets" : "/api/apps/roomrequest/v1/workspaces/lab-a/requests")
            .with(as(owner,"requester")).contentType("application/json").content(json.writeValueAsString(ticket
                ? Map.of("clientRequestId",UUID.randomUUID(),"title","Original request","description","One computer cannot connect to Wi-Fi")
                : Map.of("room","A101")))).andExpect(status().isCreated()));
    }
    private String route(JsonNode created) { return "/api/hpdi/v1/workspaces/lab-a/instances/"+created.path("process").path("processInstanceId").asText()+"/request"; }
    private JsonNode read(String route,String owner) throws Exception {
        return body(mvc.perform(get(route).with(as(owner,"requester"))).andExpect(status().isOk()));
    }
    private String update(JsonNode view,boolean ticket) throws Exception {
        return json.writeValueAsString(Map.of("expectedVersion",view.path("version").asText(),"reason","Correct original details",
            "values",ticket ? Map.of("title","Updated request","description","One computer in A101 cannot connect to Wi-Fi since this morning") : Map.of("room","A102")));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void sameApiEditsAndCancelsBothApplicationsWithHistoryAndEmail(boolean ticket) throws Exception {
        String owner="request-owner-"+UUID.randomUUID();
        var created=create(ticket,owner); String route=route(created);
        var before=read(route,owner);
        assertTrue(before.path("editable").asBoolean());
        assertFalse(before.path("closable").asBoolean());
        if (ticket) {
            mvc.perform(post(route+"/close").with(as(owner,"requester")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("expectedVersion",before.path("version").asText(),"reason","Cannot close before review"))))
                .andExpect(status().isConflict());
            for (String action:List.of("cancel","close"))
                mvc.perform(post(route+"/"+action).with(as("request-coordinator","coordinator")).contentType("application/json")
                    .content(json.writeValueAsString(Map.of("expectedVersion",before.path("version").asText(),"reason","Not the owner"))))
                    .andExpect(status().isForbidden());
            mvc.perform(patch(route).with(as("request-coordinator","coordinator")).contentType("application/json")
                .content(update(before,true))).andExpect(status().isForbidden());
        }
        mvc.perform(get(route).with(as("unrelated","requester"))).andExpect(status().isNotFound());
        mvc.perform(get(route).with(jwt().jwt(t -> t.subject(owner).claim("groups",List.of("/workspaces/other/requester")))))
            .andExpect(status().isForbidden());
        var edited=body(mvc.perform(patch(route).with(as(owner,"requester")).contentType("application/json")
            .content(update(before,ticket))).andExpect(status().isOk()));
        assertNotEquals(before.path("version").asText(),edited.path("version").asText());
        assertEquals("UPDATED",edited.path("history").get(0).path("action").asText());
        mvc.perform(patch(route).with(as(owner,"requester")).contentType("application/json").content(update(before,ticket)))
            .andExpect(status().isConflict());
        String id=created.path("id").asText();
        if (ticket) {
            assertEquals(1L,jdbc.queryForObject("select context_revision from servicedesk_ticket where id=?::uuid",Long.class,id));
            assertEquals("NEW",jdbc.queryForObject("select status from servicedesk_intake_admission where ticket_id=?::uuid",String.class,id));
            assertNull(jdbc.queryForObject("select result::text from servicedesk_assessment where ticket_id=?::uuid",String.class,id));
        } else {
            assertEquals("A102",jdbc.queryForObject("select room from roomrequest_request where id=?::uuid",String.class,id));
        }
        var cancelled=body(mvc.perform(post(route+"/cancel").with(as(ticket ? owner : "request-coordinator",ticket ? "requester" : "coordinator")).contentType("application/json")
            .content(json.writeValueAsString(Map.of("expectedVersion",edited.path("version").asText(),"reason","Requester asked to cancel"))))
            .andExpect(status().isOk()));
        assertTrue(cancelled.path("cancelled").asBoolean());
        assertFalse(cancelled.path("editable").asBoolean());
        assertEquals(2,cancelled.path("history").size());
        String process=created.path("process").path("processInstanceId").asText();
        assertEquals(0L,jdbc.queryForObject("select count(*) from act_ru_task where proc_inst_id_=?",Long.class,process));
        assertEquals(1L,jdbc.queryForObject("select count(*) from act_hi_procinst where id_=? and delete_reason_ like 'REQUEST_CANCELLED:%'",Long.class,process));
        assertTrue(jdbc.queryForObject("select count(*) from hpdi_notification n join hpdi_notification_recipient r on r.notification_id=n.id"
            + " where n.type='REQUEST_CANCELLED' and n.business_key=? and r.subject=? and r.email_status='PENDING'",Long.class,id,ticket ? "request-coordinator" : owner)>0);
        verify(live).toUser("lab-a",owner,"REQUEST_CANCELLED","request",process);
        mvc.perform(patch(route).with(as(owner,"requester")).contentType("application/json").content(update(cancelled,ticket)))
            .andExpect(status().isConflict());
        if (ticket) mvc.perform(get("/api/apps/servicedesk/v1/workspaces/lab-a/tickets/"+id).with(as(owner,"requester")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CANCELLED"));
    }
    @ParameterizedTest @ValueSource(strings={"approved","rejected"})
    void roomDecisionEmailsOwnerAndPreventsStaleEdits(String outcome) throws Exception {
        String owner="approved-owner-"+UUID.randomUUID(); var created=create(false,owner); String route=route(created);
        var before=read(route,owner);
        String process=created.path("process").path("processInstanceId").asText();
        String task=jdbc.queryForObject("select id_ from act_ru_task where proc_inst_id_=?",String.class,process);
        String path="/api/hpdi/v1/workspaces/lab-a/tasks/"+task;
        mvc.perform(post(path+"/claim").with(as("request-coordinator","coordinator"))).andExpect(status().isOk());
        mvc.perform(post(path+"/complete").with(as("request-coordinator","coordinator")).contentType("application/json")
            .content(json.writeValueAsString(Map.of("expectedTaskKey","approve","outcome",outcome)))).andExpect(status().isNoContent());
        assertFalse(read(route,owner).path("editable").asBoolean());
        mvc.perform(patch(route).with(as(owner,"requester")).contentType("application/json").content(update(before,false)))
            .andExpect(status().isConflict());
        assertEquals(1L,jdbc.queryForObject("select count(*) from hpdi_notification n join hpdi_notification_recipient r on r.notification_id=n.id"
            + " where n.type=? and n.business_key=? and r.subject=? and r.email_status='PENDING'",Long.class,
            "approved".equals(outcome) ? "ROOM_REQUEST_APPROVED" : "ROOM_REQUEST_REJECTED",created.path("id").asText(),owner));
        verify(live).toUser("lab-a",owner,"REQUEST_COMPLETED","request",process);
    }
    @Test void failureRollsBackAppDataHistoryAndMailOutbox() throws Exception {
        String owner="rollback-owner-"+UUID.randomUUID(); var created=create(false,owner); String route=route(created);
        var before=read(route,owner);
        ActivityJournal target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(journal);
        doThrow(CapabilityException.conflict("Injected audit failure")).when(target).record(eq("lab-a"),argThat(a -> a!=null && a.action().equals("request.update")));
        try {
            mvc.perform(patch(route).with(as(owner,"requester")).contentType("application/json").content(update(before,false)))
                .andExpect(status().isConflict());
        } finally { reset(target); }
        assertEquals("A101",jdbc.queryForObject("select room from roomrequest_request where id=?::uuid",String.class,created.path("id").asText()));
        assertEquals(0,read(route,owner).path("history").size());
        assertEquals(0L,jdbc.queryForObject("select count(*) from hpdi_notification where type='REQUEST_UPDATED' and business_key=?",Long.class,created.path("id").asText()));
    }
    @Test void onlyOwnerCanCloseAfterTicketApprovalAndSlaStops() throws Exception {
        String owner="approved-ticket-"+UUID.randomUUID(); var created=create(true,owner);
        String id=created.path("id").asText(), route=route(created);
        String ticketPath="/api/apps/servicedesk/v1/workspaces/lab-a/tickets/"+id;
        var intake=body(mvc.perform(get(ticketPath+"/intake").with(as("request-coordinator","coordinator"))).andExpect(status().isOk()));
        var released=body(mvc.perform(post(ticketPath+"/intake/manual-release").with(as("request-coordinator","coordinator"))
            .contentType("application/json").content(json.writeValueAsString(Map.of("expectedContextRevision",0,
                "expectedVersion",intake.path("version").asLong(),"operationId",UUID.randomUUID(),"reason","Manual admission for integration test"))))
            .andExpect(status().isOk()));
        var decision=json.createObjectNode().put("category","OTHER").put("priority","MEDIUM").put("estimateMinutes",30)
            .putNull("assignee").put("reason","Manual review").put("expectedContextRevision",0)
            .put("expectedIntakeVersion",released.path("version").asLong());
        var approved=body(mvc.perform(post(ticketPath+"/review").with(as("request-coordinator","coordinator"))
            .contentType("application/json").content(json.writeValueAsString(decision))).andExpect(status().isOk()));
        String task=approved.path("taskId").asText();
        var mine=read(route,owner);
        assertFalse(mine.path("editable").asBoolean()); assertFalse(mine.path("cancellable").asBoolean());
        assertTrue(mine.path("closable").asBoolean());
        mvc.perform(patch(route).with(as(owner,"requester")).contentType("application/json")
            .content(update(mine,true))).andExpect(status().isConflict());
        mvc.perform(post(route+"/cancel").with(as(owner,"requester")).contentType("application/json")
            .content(json.writeValueAsString(Map.of("expectedVersion",mine.path("version").asText(),"reason","Cancel after review"))))
            .andExpect(status().isConflict());
        var current=body(mvc.perform(get(route).with(as("request-coordinator","coordinator"))).andExpect(status().isOk()));
        assertFalse(current.path("closable").asBoolean()); assertFalse(current.path("cancellable").asBoolean());
        mvc.perform(post(route+"/cancel").with(as("request-coordinator","coordinator")).contentType("application/json")
            .content(json.writeValueAsString(Map.of("expectedVersion",current.path("version").asText(),"reason","Coordinator cancellation"))))
            .andExpect(status().isForbidden());
        mvc.perform(post(route+"/close").with(as("request-coordinator","coordinator")).contentType("application/json")
            .content(json.writeValueAsString(Map.of("expectedVersion",current.path("version").asText(),"reason","Not owner"))))
            .andExpect(status().isForbidden());
        var closed=body(mvc.perform(post(route+"/close").with(as(owner,"requester")).contentType("application/json")
            .content(json.writeValueAsString(Map.of("expectedVersion",mine.path("version").asText(),"reason","No longer need this work"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cancelled").value(false)));
        assertEquals("CLOSED",closed.path("history").get(0).path("action").asText());
        assertFalse(closed.path("closable").asBoolean());
        mvc.perform(get(ticketPath).with(as(owner,"requester"))).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CLOSED"));
        mvc.perform(post(route+"/close").with(as(owner,"requester")).contentType("application/json")
            .content(json.writeValueAsString(Map.of("expectedVersion",closed.path("version").asText(),"reason","Repeat close"))))
            .andExpect(status().isConflict());
        String process=created.path("process").path("processInstanceId").asText();
        assertEquals(0L,jdbc.queryForObject("select count(*) from act_ru_task where proc_inst_id_=?",Long.class,process));
        assertEquals(1L,jdbc.queryForObject("select count(*) from act_hi_procinst where id_=? and delete_reason_ like 'REQUEST_CLOSED:%'",Long.class,process));
        assertTrue(jdbc.queryForObject("select count(*) from hpdi_notification n join hpdi_notification_recipient r on r.notification_id=n.id"
            + " where n.type='REQUEST_CLOSED' and n.business_key=? and r.subject='request-coordinator' and r.email_status='PENDING'",Long.class,id)>0);
        verify(live).toChannel("lab-a",LiveUpdates.WORK,"REQUEST_CLOSED","request",process);
        assertNotNull(jdbc.queryForObject("select released_at from servicedesk_sla where handle_task_id=?",java.sql.Timestamp.class,task));
        assertEquals(0L,jdbc.queryForObject("select count(*) from servicedesk_alert_monitor where ticket_id=?::uuid and active",Long.class,id));
    }
    @Test void editsStartFreshContextAndPreserveEarlierAnswers() throws Exception {
        String owner="context-owner-"+UUID.randomUUID(); var created=create(true,owner);
        UUID id=UUID.fromString(created.path("id").asText()); String route=route(created);
        String answers="/api/apps/servicedesk/v1/workspaces/lab-a/tickets/"+id+"/clarifications";
        for (int round=0;round<2;round++) {
            long revision=contexts.state("lab-a",id,false).orElseThrow().revision();
            mvc.perform(post(answers).with(as(owner,"requester")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("contractVersion","ticket-clarification@0.1.0","clientAnswerId",UUID.randomUUID(),
                    "expectedContextRevision",revision,"answers",List.of(Map.of("slot","device.identifier","value","PC-"+round,"correction",false))))))
                .andExpect(status().isCreated());
            var state=contexts.state("lab-a",id,false).orElseThrow();
            assertEquals(3,snapshots.assemble("lab-a",state,contexts.events("lab-a",id)).sources().size());
            var view=read(route,owner);
            String title="Updated request "+round;
            mvc.perform(patch(route).with(as(owner,"requester")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("expectedVersion",view.path("version").asText(),"reason","Correct request content",
                    "values",Map.of("title",title,"description","One computer cannot connect to Wi-Fi")))))
                .andExpect(status().isOk());
            state=contexts.state("lab-a",id,false).orElseThrow();
            var snapshot=snapshots.assemble("lab-a",state,contexts.events("lab-a",id));
            assertEquals(state.revision(),snapshot.contextRevision());
            assertEquals(2,snapshot.sources().size());
            assertEquals(title,snapshot.sources().get(0).text());
            assertEquals((long)round+1,jdbc.queryForObject("select count(*) from servicedesk_ticket_clarification where ticket_id=?",Long.class,id));
        }
    }
    @Test void concurrentEditsCannotOverwriteEachOther() throws Exception {
        String owner="concurrent-owner-"+UUID.randomUUID(); var created=create(false,owner); String route=route(created);
        String payload=update(read(route,owner),false);
        var start=new CountDownLatch(1); var executor=Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> operation=()->{ start.await(); return mvc.perform(patch(route).with(as(owner,"requester"))
                .contentType("application/json").content(payload)).andReturn().getResponse().getStatus(); };
            var first=executor.submit(operation); var second=executor.submit(operation); start.countDown();
            var statuses=new ArrayList<>(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))); Collections.sort(statuses);
            assertEquals(List.of(200,409),statuses);
            assertEquals(1,read(route,owner).path("history").size());
        } finally { executor.shutdownNow(); }
    }
}
