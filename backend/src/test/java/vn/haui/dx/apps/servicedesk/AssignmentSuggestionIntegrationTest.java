package vn.haui.dx.apps.servicedesk;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.haui.dx.core.identity.api.*;
import vn.haui.dx.core.workflow.api.WorkflowService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="FILE_TEST_JDBC_URL", matches=".+/file_storage")
class AssignmentSuggestionIntegrationTest {
    private static final String BASE="/api/apps/servicedesk/v1/workspaces/lab-a/tickets";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",() -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username",() -> "workflow");
        properties.add("spring.datasource.password",() -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",() -> "http://127.0.0.1:1/jwks");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired WorkspaceDirectory directory;
    @SpyBean WorkflowService workflow;
    private String first;
    private String second;
    private String owner;

    private RequestPostProcessor as(String subject, String role) {
        return jwt().jwt(token -> token.subject(subject).claim("groups",List.of("/workspaces/lab-a/"+role)));
    }
    @BeforeEach void agents() {
        String suffix=UUID.randomUUID().toString();
        first="a-"+suffix; second="b-"+suffix; owner="owner-"+suffix;
        members(first,second,owner);
        when(directory.supportGroupMembers("lab-a","hardware")).thenReturn(Set.of(first,second,owner));
        for (String subject : List.of(first,second,owner))
            jdbc.update("insert into hpdi_agent_duty(workspace,subject,on_duty) values('lab-a',?,true)",subject);
    }
    private void members(String... subjects) {
        when(directory.members("lab-a")).thenReturn(Arrays.stream(subjects)
            .map(subject -> new DirectoryMember(subject,subject,null,Set.of(WorkspaceRole.AGENT))).toList());
    }
    private JsonNode create() throws Exception {
        return json.readTree(mvc.perform(post(BASE).with(as(owner,"requester")).contentType("application/json")
            .content(json.writeValueAsString(Map.of("clientRequestId",UUID.randomUUID(),"title","R2 test","description","Synthetic assignment test"))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private String preview(JsonNode ticket) {
        return BASE+"/"+ticket.path("id").asText()+"/assignment-suggestion?category=HARDWARE&priority=HIGH";
    }
    private JsonNode suggestion(JsonNode ticket) throws Exception {
        var response=json.readTree(mvc.perform(get(preview(ticket)).with(as("coordinator","coordinator")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        contract(response);
        return response;
    }
    private void contract(JsonNode response) throws Exception {
        var path=java.nio.file.Path.of("../contracts/hpdi/openapi.yaml");
        Map<String,Object> specification=new org.yaml.snakeyaml.Yaml().load(java.nio.file.Files.readString(path));
        var document=json.valueToTree(specification);
        var schemas=json.createObjectNode();
        for (String name : List.of("AssignmentCandidate","AssignmentSuggestion"))
            schemas.set(name,document.path("components").path("schemas").path(name).deepCopy());
        normalize(schemas);
        var root=json.createObjectNode().put("$ref","#/components/schemas/AssignmentSuggestion");
        root.set("components",json.createObjectNode().set("schemas",schemas));
        var schema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V7).getSchema(root);
        assertTrue(schema.validate(response).isEmpty(),() -> schema.validate(response).toString());
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/r2-assignment-http-evidence.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(response));
    }
    private void normalize(JsonNode node) {
        if (node.isObject() && node.path("nullable").asBoolean() && node.path("type").isTextual()) {
            var object=(com.fasterxml.jackson.databind.node.ObjectNode)node;
            object.set("type",json.createArrayNode().add(node.path("type").asText()).add("null"));
            object.remove("nullable");
        }
        node.elements().forEachRemaining(this::normalize);
    }
    private JsonNode review(JsonNode ticket, String assignee, int minutes) throws Exception {
        var input=json.createObjectNode().put("category","HARDWARE").put("priority","HIGH").put("estimateMinutes",minutes).put("reason","Coordinator decision");
        if (assignee==null) input.putNull("assignee"); else input.put("assignee",assignee);
        return json.readTree(mvc.perform(post(BASE+"/"+ticket.path("id").asText()+"/review").with(as("coordinator","coordinator"))
            .contentType("application/json").content(input.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private List<Map<String,Object>> events() {
        return jdbc.queryForList("select * from servicedesk_assignment_event order by id");
    }

    private JsonNode handleSuggestion(String taskId) throws Exception {
        var response=json.readTree(mvc.perform(get("/api/hpdi/v1/workspaces/lab-a/tasks/"+taskId+"/assignment-suggestion")
            .with(as("coordinator","coordinator"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        contract(response);
        return response;
    }

    private JsonNode candidate(JsonNode response, String subject) {
        return java.util.stream.StreamSupport.stream(response.path("candidates").spliterator(),false)
            .filter(candidate -> subject.equals(candidate.path("subject").asText())).findFirst().orElseThrow();
    }

    @Test void reassignmentExcludesOnlyTheConsideredTaskWithoutChangingQueueOrAssignmentHistory() throws Exception {
        String target=review(create(),first,90).path("taskId").asText();
        review(create(),first,20);
        review(create(),second,60);
        var preview=suggestion(create());
        assertEquals(110,candidate(preview,first).path("loadMinutes").asLong());
        assertEquals(60,candidate(preview,second).path("loadMinutes").asLong());
        assertEquals(second,preview.path("recommended").asText());
        var beforeEvents=events();
        var beforeCursor=jdbc.queryForList("select * from servicedesk_assignment_cursor order by workspace,support_group");
        for (String state : List.of("NOT_STARTED","IN_PROGRESS","PENDING")) {
            if ("IN_PROGRESS".equals(state))
                mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/"+target+"/start").with(as(first,"agent")))
                    .andExpect(status().isOk());
            if ("PENDING".equals(state))
                mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/"+target+"/pause").with(as(first,"agent"))
                    .contentType("application/json").content("{\"reason\":\"WAITING_EXTERNAL\"}"))
                    .andExpect(status().isOk());
            var result=handleSuggestion(target);
            assertEquals(first,result.path("recommended").asText(),state);
            assertEquals(20,candidate(result,first).path("loadMinutes").asLong(),state);
            assertEquals(60,candidate(result,second).path("loadMinutes").asLong(),state);
            assertEquals(2,candidate(result,first).path("assignedToday").asLong(),state);
            assertEquals(1,candidate(result,second).path("assignedToday").asLong(),state);
            var queue=json.readTree(mvc.perform(get("/api/hpdi/v1/workspaces/lab-a/queue").param("assignee",first)
                .with(as("coordinator","coordinator"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var current=java.util.stream.StreamSupport.stream(queue.path("items").spliterator(),false)
                .filter(item -> target.equals(item.path("id").asText())).findFirst().orElseThrow();
            assertEquals(state,current.path("workState").asText());
            assertEquals(20+current.path("remainingMinutes").asLong(),queue.path("loadMinutes").asLong());
            assertEquals(beforeEvents,events());
            assertEquals(beforeCursor,jdbc.queryForList("select * from servicedesk_assignment_cursor order by workspace,support_group"));
        }
    }

    @Test void previewIsReadOnlyAndCoordinatorCanOverrideThenEligibilityIsRechecked() throws Exception {
        var ticket=create();
        var beforeEvents=events();
        var beforeCursor=jdbc.queryForList("select * from servicedesk_assignment_cursor order by workspace,support_group");
        long audits=jdbc.queryForObject("select count(*) from audit_event",Long.class);
        clearInvocations(workflow);
        var suggested=suggestion(ticket);
        assertNotEquals(owner,suggested.path("recommended").asText());
        assertEquals(suggested,suggestion(ticket));
        var scans = org.mockito.ArgumentCaptor.forClass(vn.haui.dx.core.common.PageRequest.class);
        verify(workflow,atLeast(2)).workspaceQueue(eq("lab-a"),scans.capture());
        var requests = scans.getAllValues();
        assertEquals(0, requests.size() % 2);
        int pages = requests.size() / 2;
        for (int scan = 0; scan < 2; scan++) {
            for (int page = 0; page < pages; page++) {
                assertEquals(page, requests.get(scan * pages + page).page());
                assertEquals(100, requests.get(scan * pages + page).size());
            }
        }
        assertEquals(beforeEvents,events());
        assertEquals(beforeCursor,jdbc.queryForList("select * from servicedesk_assignment_cursor order by workspace,support_group"));
        assertEquals(audits,jdbc.queryForObject("select count(*) from audit_event",Long.class));
        String override=suggested.path("recommended").asText().equals(first) ? second : first;
        var reviewed=review(ticket,override,90);
        assertEquals(override,reviewed.path("assignee").asText());
        assertEquals(beforeEvents.size()+1,events().size());
        String task=reviewed.path("taskId").asText();
        String route="/api/hpdi/v1/workspaces/lab-a/tasks/"+task;
        contract(json.readTree(mvc.perform(get(route+"/assignment-suggestion").with(as("coordinator","coordinator")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()));
        String target=override.equals(first) ? second : first;
        jdbc.update("update hpdi_agent_duty set on_duty=false where workspace='lab-a' and subject=?",target);
        mvc.perform(post(route+"/assign").with(as("coordinator","coordinator")).contentType("application/json")
            .content("{\"assignee\":\""+target+"\"}")).andExpect(status().isConflict());
        members(override,owner);
        mvc.perform(post(route+"/assign").with(as("coordinator","coordinator")).contentType("application/json")
            .content("{\"assignee\":\""+target+"\"}")).andExpect(status().isBadRequest());
        assertEquals(beforeEvents.size()+1,events().size());
    }

    @Test void workloadDailyHistoryAndPersistentCursorAreWorkspaceAndGroupIsolated() throws Exception {
        var firstWork=review(create(),first,60);
        var ticket=create();
        assertEquals(second,suggestion(ticket).path("recommended").asText());
        jdbc.update("insert into servicedesk_assignment_event(workspace,process_id,subject) values('lab-a','completed-work',?)",second);
        jdbc.update("insert into servicedesk_assignment_event(workspace,process_id,subject) values('lab-b','other-workspace',?)",first);
        var result=suggestion(ticket);
        var firstCandidate=java.util.stream.StreamSupport.stream(result.path("candidates").spliterator(),false)
            .filter(candidate -> first.equals(candidate.path("subject").asText())).findFirst().orElseThrow();
        assertEquals(60,firstCandidate.path("loadMinutes").asInt());
        assertEquals(1,firstCandidate.path("assignedToday").asInt());
        assertEquals(first,jdbc.queryForObject("select last_subject from servicedesk_assignment_cursor where workspace='lab-a' and support_group='hardware'",String.class));
        mvc.perform(get(preview(ticket).replace("lab-a","lab-b")).with(as("coordinator","coordinator"))).andExpect(status().isForbidden());
        mvc.perform(get(preview(ticket)).with(as(first,"agent"))).andExpect(status().isForbidden());
        mvc.perform(get(preview(ticket).replace("HARDWARE","OTHER")).with(as("coordinator","coordinator")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.recommended").isEmpty());
        assertNotNull(firstWork.path("taskId").asText());
    }

    @Test void offDutyWrongGroupRequesterAndRevocationProduceNoRecommendation() throws Exception {
        var ticket=create();
        when(directory.supportGroupMembers("lab-a","hardware")).thenReturn(Set.of(first,owner));
        jdbc.update("update hpdi_agent_duty set on_duty=false where workspace='lab-a' and subject=?",first);
        var result=suggestion(ticket);
        assertTrue(result.path("recommended").isNull());
        assertEquals(2,result.path("candidates").size());
        members();
        assertEquals(0,suggestion(ticket).path("candidates").size());
    }

    @Test void dailyCountsRetainReassignmentAndCompletedWorkButIgnoreNoOpAssignments() throws Exception {
        var ticket=create();
        String task=review(ticket,first,90).path("taskId").asText();
        String route="/api/hpdi/v1/workspaces/lab-a/tasks/"+task;
        int before=events().size();
        mvc.perform(post(route+"/assign").with(as("coordinator","coordinator")).contentType("application/json")
            .content(json.writeValueAsString(Map.of("assignee",first)))).andExpect(status().isOk());
        assertEquals(before,events().size());
        mvc.perform(post(route+"/assign").with(as("coordinator","coordinator")).contentType("application/json")
            .content(json.writeValueAsString(Map.of("assignee",second)))).andExpect(status().isOk());
        assertEquals(before+1,events().size());
        var reassignment=handleSuggestion(task);
        assertEquals(1,candidate(reassignment,first).path("assignedToday").asLong());
        assertEquals(1,candidate(reassignment,second).path("assignedToday").asLong());
        assertEquals(0,candidate(reassignment,second).path("loadMinutes").asLong());
        mvc.perform(post(route+"/start").with(as(second,"agent"))).andExpect(status().isOk());
        mvc.perform(post(route+"/complete").with(as(second,"agent")).contentType("application/json")
            .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}")).andExpect(status().isNoContent());
        var next=create();
        var afterCompletion=suggestion(next);
        assertEquals(0,candidate(afterCompletion,second).path("loadMinutes").asLong());
        assertEquals(1,candidate(afterCompletion,second).path("assignedToday").asLong());
        String unclaimed=review(next,null,30).path("taskId").asText();
        mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/"+unclaimed+"/claim").with(as(first,"agent")))
            .andExpect(status().isOk());
        assertEquals(2,candidate(handleSuggestion(unclaimed),first).path("assignedToday").asLong());
        assertEquals(before+2,events().size());
    }

    @Test void dailyCountsUseConfiguredBusinessDayBoundariesAndWorkspace() throws Exception {
        var day=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        var start=day.atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        var end=day.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        for (var instant : List.of(start.minusMillis(1),start,end))
            jdbc.update("insert into servicedesk_assignment_event(workspace,process_id,subject,assigned_at) values('lab-a','day-boundary',?,?)",
                first,java.sql.Timestamp.from(instant));
        jdbc.update("insert into servicedesk_assignment_event(workspace,process_id,subject,assigned_at) values('lab-b','other-workspace',?,?)",
            first,java.sql.Timestamp.from(start));
        var result=suggestion(create());
        assertEquals(1,candidate(result,first).path("assignedToday").asLong());
        assertEquals(0,candidate(result,second).path("assignedToday").asLong());
    }

    @Test void failedAssignmentRollsBackEventCursorAndFlowableAssignee() throws Exception {
        String task=review(create(),null,60).path("taskId").asText();
        var beforeEvents=events();
        var beforeCursor=jdbc.queryForList("select * from servicedesk_assignment_cursor order by workspace,support_group");
        jdbc.execute("create function r2_reject_assignment_audit() returns trigger language plpgsql as $$ begin"
            + " if new.action='task.assign' then raise exception 'R2 audit rollback' using errcode='23514'; end if; return new; end $$");
        jdbc.execute("create trigger r2_reject_assignment_audit before insert on audit_event for each row execute function r2_reject_assignment_audit()");
        try {
            mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/"+task+"/assign").with(as("coordinator","coordinator"))
                .contentType("application/json").content(json.writeValueAsString(Map.of("assignee",first))))
                .andExpect(status().isConflict());
            assertEquals(beforeEvents,events());
            assertEquals(beforeCursor,jdbc.queryForList("select * from servicedesk_assignment_cursor order by workspace,support_group"));
            assertEquals(0,candidate(handleSuggestion(task),first).path("assignedToday").asLong());
            assertNull(jdbc.queryForObject("select assignee_ from act_ru_task where id_=?",String.class,task));
        } finally {
            jdbc.execute("drop trigger r2_reject_assignment_audit on audit_event");
            jdbc.execute("drop function r2_reject_assignment_audit()");
        }
    }

    @Test void concurrentExplicitAssignmentsCommitExactlyOneCursorRowAndAllEvents() throws Exception {
        var firstTask=review(create(),null,60).path("taskId").asText();
        var secondTask=review(create(),null,60).path("taskId").asText();
        int before=events().size();
        var workers=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        jdbc.update("insert into servicedesk_assignment_cursor(workspace,support_group,last_subject) values('lab-a','hardware','prior-agent')"
            + " on conflict(workspace,support_group) do update set last_subject=excluded.last_subject");
        jdbc.update("insert into servicedesk_assignment_cursor(workspace,support_group,last_subject) values('lab-b','hardware','other-workspace'),"
            + " ('lab-a','software','other-group') on conflict do nothing");
        var otherCursors=jdbc.queryForList("select * from servicedesk_assignment_cursor where workspace<>'lab-a' or support_group<>'hardware' order by workspace,support_group");
        try (var blocker=java.sql.DriverManager.getConnection(System.getenv("FILE_TEST_JDBC_URL"),"workflow",System.getenv("FILE_TEST_DB_PASSWORD"))) {
            blocker.setAutoCommit(false);
            try (var statement=blocker.prepareStatement("select last_subject from servicedesk_assignment_cursor where workspace='lab-a' and support_group='hardware' for update")) {
                try (var rows=statement.executeQuery()) { assertTrue(rows.next()); }
            }
            var results=new ArrayList<java.util.concurrent.Future<Integer>>();
            for (var assignment : Map.of(firstTask,first,secondTask,second).entrySet())
                results.add(workers.submit(() -> {
                    start.await();
                    return mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/"+assignment.getKey()+"/assign")
                        .with(as("coordinator","coordinator")).contentType("application/json")
                        .content("{\"assignee\":\""+assignment.getValue()+"\"}")).andReturn().getResponse().getStatus();
                }));
            start.countDown();
            int waiting=0;
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
            while (waiting<2 && System.nanoTime()<deadline) {
                waiting=jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database()"
                    + " and wait_event_type='Lock' and query like 'insert into servicedesk_assignment_cursor%'",Integer.class);
                if (waiting<2) Thread.sleep(25);
            }
            assertEquals(2,waiting,"Both real assignment transactions must overlap at the cursor lock");
            assertEquals(before,events().size());
            assertEquals(first,handleSuggestion(firstTask).path("recommended").asText());
            assertEquals("prior-agent",jdbc.queryForObject("select last_subject from servicedesk_assignment_cursor where workspace='lab-a' and support_group='hardware'",String.class));
            blocker.commit();
            for (var result : results) assertEquals(200,result.get(30,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(before+2,events().size());
            assertEquals(1,jdbc.queryForObject("select count(*) from servicedesk_assignment_cursor where workspace='lab-a' and support_group='hardware'",Integer.class));
            assertTrue(Set.of(first,second).contains(jdbc.queryForObject("select last_subject from servicedesk_assignment_cursor where workspace='lab-a' and support_group='hardware'",String.class)));
            String cursor=jdbc.queryForObject("select last_subject from servicedesk_assignment_cursor where workspace='lab-a' and support_group='hardware'",String.class);
            var next=suggestion(create());
            assertEquals(1,candidate(next,first).path("assignedToday").asLong());
            assertEquals(1,candidate(next,second).path("assignedToday").asLong());
            assertEquals(cursor.equals(first) ? second : first,next.path("recommended").asText());
            assertEquals(otherCursors,jdbc.queryForList("select * from servicedesk_assignment_cursor where workspace<>'lab-a' or support_group<>'hardware' order by workspace,support_group"));
        } finally { start.countDown(); workers.shutdownNow(); }
    }
}
