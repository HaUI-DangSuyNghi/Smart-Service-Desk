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

    @Test void previewIsReadOnlyAndCoordinatorCanOverrideThenEligibilityIsRechecked() throws Exception {
        var ticket=create();
        var beforeEvents=events();
        var beforeCursor=jdbc.queryForList("select * from servicedesk_assignment_cursor order by workspace,support_group");
        long audits=jdbc.queryForObject("select count(*) from audit_event",Long.class);
        clearInvocations(workflow);
        var suggested=suggestion(ticket);
        assertNotEquals(owner,suggested.path("recommended").asText());
        assertEquals(suggested,suggestion(ticket));
        verify(workflow,times(2)).workspaceQueue(eq("lab-a"),any());
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

    @Test void concurrentExplicitAssignmentsCommitExactlyOneCursorRowAndAllEvents() throws Exception {
        var firstTask=review(create(),null,60).path("taskId").asText();
        var secondTask=review(create(),null,60).path("taskId").asText();
        int before=events().size();
        var workers=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try {
            var results=new ArrayList<java.util.concurrent.Future<Integer>>();
            for (var assignment : Map.of(firstTask,first,secondTask,second).entrySet())
                results.add(workers.submit(() -> {
                    start.await();
                    return mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/"+assignment.getKey()+"/assign")
                        .with(as("coordinator","coordinator")).contentType("application/json")
                        .content("{\"assignee\":\""+assignment.getValue()+"\"}")).andReturn().getResponse().getStatus();
                }));
            start.countDown();
            for (var result : results) assertEquals(200,result.get(30,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(before+2,events().size());
            assertEquals(1,jdbc.queryForObject("select count(*) from servicedesk_assignment_cursor where workspace='lab-a' and support_group='hardware'",Integer.class));
            assertTrue(Set.of(first,second).contains(jdbc.queryForObject("select last_subject from servicedesk_assignment_cursor where workspace='lab-a' and support_group='hardware'",String.class)));
        } finally { start.countDown(); workers.shutdownNow(); }
    }
}
