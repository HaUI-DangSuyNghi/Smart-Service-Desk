package vn.haui.dx.apps.servicedesk.intake;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.mock.mockito.MockBean;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextProcessingGate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.haui.dx.apps.servicedesk.ServiceDeskProcess;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"serviceDesk.intake.new-tickets=true", "serviceDesk.intake.true-wait=true",
        "serviceDesk.intake.canonical-human-enabled=true", "serviceDesk.context.enabled=true", "serviceDesk.context.mode=ASSISTED", "service-desk.background.scheduled=false"})
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(vn.haui.dx.core.workflow.internal.WorkflowStateFixture.class)
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class CanonicalTicketIntegrationTest {
    private static final String BASE = "/api/apps/servicedesk/v1/workspaces/canonical-a/tickets";
    private static final String TASKS = "/api/hpdi/v1/workspaces/canonical-a/tasks/";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired vn.haui.dx.core.workflow.internal.WorkflowStateFixture workflow;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        registry.add("spring.datasource.username", () -> "workflow");
        registry.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
    }

    @MockBean ContextProcessingGate rights;

    @BeforeEach
    void authorizeSyntheticData() {
        when(rights.verify(anyString(), anyString())).thenReturn("synthetic-grant");
        when(rights.verifyReadiness(anyString(), anyString(), anyString())).thenReturn("synthetic-grant");
    }

    @Test
    void closedRowsBeyondFiftyDoNotHideAnOlderOpenRoot() throws Exception {
        var root = root();
        for (int index = 0; index < 51; index++) {
            var closed = create("Synthetic canceled predecessor " + index);
            workflow.cancel(closed.path("process").path("processInstanceId").asText());
        }
        var follower = create();
        var probe = body(mvc.perform(post(path(follower) + "/intake/duplicate-preflight").with(as("coordinator", "coordinator")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(probe.path("scanned").asInt() > 50);
        assertFalse(probe.path("candidateSetIncomplete").asBoolean());
        assertTrue(probe.path("candidates").toString().contains(id(root).toString()));
        link(root, follower, 200);
    }

    @Test
    void replayCannotResurrectALinkAfterSplit() throws Exception {
        var root = root();
        var follower = create();
        var command = command(root, follower);
        for (int index = 0; index < 2; index++) {
            mvc.perform(post(path(follower) + "/canonical").with(as("coordinator", "coordinator")).contentType("application/json")
                    .content(json.writeValueAsString(command))).andExpect(status().isOk());
        }
        mvc.perform(post(path(follower) + "/canonical/split").with(as("coordinator", "coordinator")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("expectedLinkVersion", 1, "expectedContextRevision", 0,
                        "operationId", UUID.randomUUID(), "reason", "Synthetic split")))).andExpect(status().isOk());
        mvc.perform(post(path(follower) + "/canonical").with(as("coordinator", "coordinator")).contentType("application/json")
                .content(json.writeValueAsString(command))).andExpect(status().isConflict());
        assertEquals("INTAKE", getTicket(follower).path("state").asText());
    }

    @Test
    void humanLinkKeepsOneProcessAndRedactsRootForRequester() throws Exception {
        var root = root();
        var follower = create();
        var result = link(root, follower, 200);
        assertEquals("ACTIVE", result.path("status").asText());
        assertEquals("FOLLOWING", getTicket(follower).path("state").asText());
        assertEquals(follower.path("process"), getTicket(follower).path("process"));
        mvc.perform(get(path(follower) + "/canonical").with(as("owner", "requester")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rootTicketId").isEmpty());
        var task = getTicket(follower).path("taskId").asText();
        mvc.perform(post(TASKS + task + "/claim").with(as("coordinator", "coordinator"))).andExpect(status().isOk());
        mvc.perform(post(TASKS + task + "/complete").with(as("coordinator", "coordinator")).contentType("application/json")
                .content("{\"expectedTaskKey\":\"follower\",\"outcome\":\"closed\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void splitReturnsToFreshIntakeRound() throws Exception {
        var root = root();
        var follower = create();
        var linked = link(root, follower, 200);
        mvc.perform(post(path(follower) + "/canonical/split").with(as("coordinator", "coordinator")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("expectedLinkVersion", linked.path("version").asLong(), "expectedContextRevision", 0,
                        "operationId", UUID.randomUUID(), "reason", "Synthetic wrong object correction"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SPLIT"));
        assertEquals("INTAKE", getTicket(follower).path("state").asText());
        assertEquals(2, state(follower).path("reviewRound").asInt());
    }

    @Test
    void distinctObjectCannotFollowRootEvenWithMatchingCategory() throws Exception {
        var root = root();
        var follower = create("Máy 16 phòng 1304 không lên nguồn");
        link(root, follower, 409);
        assertEquals("INTAKE", getTicket(follower).path("state").asText());
    }

    @Test
    void unreviewedRootCannotBeCanonicalAndRequesterCannotLink() throws Exception {
        var root = create();
        var follower = create();
        link(root, follower, 409);
        mvc.perform(post(path(follower) + "/canonical").with(as("owner", "requester")).contentType("application/json")
                .content(json.writeValueAsString(command(root, follower))))
                .andExpect(status().isForbidden());
    }

    @Test
    void closureRequiresActualRootConfirmationAndThenClosesFollower() throws Exception {
        var root = root();
        var follower = create();
        link(root, follower, 200);
        var handle = getTicket(root).path("taskId").asText();
        mvc.perform(post(TASKS + handle + "/claim").with(as("agent", "agent"))).andExpect(status().isOk());
        mvc.perform(post(TASKS + handle + "/start").with(as("agent", "agent"))).andExpect(status().isOk());
        mvc.perform(post(TASKS + handle + "/complete").with(as("agent", "agent")).contentType("application/json")
                .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}")).andExpect(status().isNoContent());
        var confirm = getTicket(root).path("taskId").asText();
        mvc.perform(post(TASKS + confirm + "/complete").with(as("owner", "requester")).contentType("application/json")
                .content("{\"expectedTaskKey\":\"confirm\",\"outcome\":\"accepted\"}")).andExpect(status().isNoContent());
        var task = getTicket(follower).path("taskId").asText();
        mvc.perform(post(TASKS + task + "/claim").with(as("coordinator", "coordinator"))).andExpect(status().isOk());
        mvc.perform(post(TASKS + task + "/complete").with(as("coordinator", "coordinator")).contentType("application/json")
                .content("{\"expectedTaskKey\":\"follower\",\"outcome\":\"closed\"}")).andExpect(status().isNoContent());
        assertEquals("CLOSED", getTicket(follower).path("state").asText());
        assertEquals(1L, jdbc.queryForObject("select count(*) from servicedesk_canonical_link where follower_id=? and state='CLOSED'", Long.class, id(follower)));
    }

    @Test
    void lateContextPreservesFollowerButPreventsSilentClosure() throws Exception {
        var root = root();
        var follower = create();
        link(root, follower, 200);
        mvc.perform(post(path(follower) + "/clarifications").with(as("owner", "requester")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("contractVersion", "ticket-clarification@0.1.0", "clientAnswerId", UUID.randomUUID(),
                        "expectedContextRevision", 0, "answers", List.of(Map.of("slot", "location.room", "value", "1305", "correction", true))))))
                .andExpect(status().isCreated());
        assertTrue(state(follower).path("lateNotice").asBoolean());
        assertEquals("CANONICAL_FOLLOWER", state(follower).path("status").asText());
    }

    private JsonNode root() throws Exception {
        var ticket = create();
        release(ticket, UUID.randomUUID());
        review(ticket, state(ticket).path("version").asLong(), 200);
        return ticket;
    }
    private Map<String, Object> command(JsonNode root, JsonNode follower) throws Exception {
        var probe = body(mvc.perform(post(path(follower) + "/intake/duplicate-preflight").with(as("coordinator", "coordinator")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertFalse(probe.path("autoMergeAllowed").asBoolean());
        return Map.of("rootTicketId", id(root), "expectedContextRevision", 0, "expectedRootRevision", 0,
                "expectedIntakeVersion", 0, "candidateSetDigest", probe.path("candidateSetDigest").asText(),
                "verifiedCategory", "HARDWARE", "operationId", UUID.randomUUID(), "reason", "Synthetic human verified same physical object");
    }
    private JsonNode link(JsonNode root, JsonNode follower, int expected) throws Exception {
        return body(mvc.perform(post(path(follower) + "/canonical").with(as("coordinator", "coordinator")).contentType("application/json")
                .content(json.writeValueAsString(command(root, follower)))).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString());
    }
    private JsonNode create() throws Exception { return create("Máy 15 phòng 1304 không lên nguồn"); }
    private JsonNode create(String title) throws Exception {
        return body(mvc.perform(post(BASE).with(as("owner", "requester")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("clientRequestId", UUID.randomUUID(), "title", title, "description", title))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode getTicket(JsonNode ticket) throws Exception {
        return body(mvc.perform(get(path(ticket)).with(as("owner", "requester")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode state(JsonNode ticket) throws Exception {
        return body(mvc.perform(get(path(ticket) + "/intake").with(as("owner", "requester")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private void release(JsonNode ticket, UUID operation) throws Exception {
        mvc.perform(post(path(ticket) + "/intake/manual-release").with(as("coordinator", "coordinator")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("expectedContextRevision", 0, "expectedVersion", 0, "operationId", operation, "reason", "Synthetic human disposition"))))
                .andExpect(status().isOk());
    }
    private void review(JsonNode ticket, long version, int expected) throws Exception {
        mvc.perform(post(path(ticket) + "/review").with(as("coordinator", "coordinator")).contentType("application/json")
                .content("{\"category\":\"HARDWARE\",\"priority\":\"HIGH\",\"assignee\":null,\"reason\":\"Synthetic review\","
                        + "\"expectedContextRevision\":0,\"expectedIntakeVersion\":" + version + "}"))
                .andExpect(status().is(expected));
    }
    private JsonNode body(String value) throws Exception { return json.readTree(value); }
    private static UUID id(JsonNode ticket) { return UUID.fromString(ticket.path("id").asText()); }
    private static String path(JsonNode ticket) { return BASE + "/" + id(ticket); }
    private static RequestPostProcessor as(String subject, String role) {
        return jwt().jwt(token -> token.subject(subject).claim("groups", List.of("/workspaces/canonical-a/" + role)));
    }
}
