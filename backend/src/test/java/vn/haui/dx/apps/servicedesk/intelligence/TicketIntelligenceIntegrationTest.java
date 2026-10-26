package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.haui.dx.hpdi.intelligence.integration.IntelligenceTransport;
import vn.haui.dx.hpdi.intelligence.integration.TriageRequest;
import vn.haui.dx.hpdi.intelligence.integration.TriageResponses;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "serviceDesk.assistant.enabled=true")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class TicketIntelligenceIntegrationTest {
    private static final String BASE = "/api/apps/servicedesk/v1/workspaces/assistant-a/tickets";
    private static final Path STORAGE = Path.of(
            System.getProperty("java.io.tmpdir"), "assistant-test-" + UUID.randomUUID());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("dx.storage.root", STORAGE::toString);
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private IntelligenceTransport transport;

    @BeforeEach
    void configureTransport() {
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return success(invocation.getArgument(0));
        }).when(transport).triage(any());
    }

    @Test
    void workspaceVisibilityAndAuthenticationAreEnforcedBeforeInference() throws Exception {
        var ticket = create();
        String route = route(ticket);

        mvc.perform(post(route).contentType("application/json").content(body(false)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(route).with(as("other", "assistant-a", "requester"))
                        .contentType("application/json").content(body(false)))
                .andExpect(status().isNotFound());
        mvc.perform(post(route).with(as("foreign", "assistant-b", "requester"))
                        .contentType("application/json").content(body(false)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(transport);
    }

    @Test
    void rulesOnlyAndDisabledGuidanceDoNotMutateTicketOrWorkflow() throws Exception {
        var ticket = create();
        long ticketsBefore = jdbc.queryForObject("select count(*) from servicedesk_ticket", Long.class);
        long tasksBefore = jdbc.queryForObject("select count(*) from act_ru_task", Long.class);

        mvc.perform(post(route(ticket)).with(as("owner", "assistant-a", "requester"))
                        .contentType("application/json").content(body(false)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("COMPLETE"))
                .andExpect(jsonPath("$.triage.category").value("NETWORK"))
                .andExpect(jsonPath("$.risk.status").value("NOT_ASSESSED"))
                .andExpect(jsonPath("$.guidance.status").value("NOT_REQUESTED"));

        mvc.perform(post(route(ticket)).with(as("owner", "assistant-a", "requester"))
                        .contentType("application/json").content(body(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.guidance.reasonCode").value("ASSISTANCE_DISABLED"));

        assertEquals(ticketsBefore, jdbc.queryForObject("select count(*) from servicedesk_ticket", Long.class));
        assertEquals(tasksBefore, jdbc.queryForObject("select count(*) from act_ru_task", Long.class));
        var current = mvc.perform(get(BASE + "/" + ticket.path("id").asText())
                        .with(as("owner", "assistant-a", "requester")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(ticket, json.readTree(current));
    }

    @Test
    void changedPersistedTicketSuppressesAssessment() throws Exception {
        var ticket = create();

        doAnswer(invocation -> {
            jdbc.update("update servicedesk_ticket set title=? where id=?",
                    "Changed during inference", UUID.fromString(ticket.path("id").asText()));
            return success(invocation.getArgument(0));
        }).when(transport).triage(any());

        mvc.perform(post(route(ticket)).with(as("owner", "assistant-a", "requester"))
                        .contentType("application/json").content(body(false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.triage").doesNotExist());
    }

    private JsonNode create() throws Exception {
        String payload = json.writeValueAsString(Map.of(
                "clientRequestId", UUID.randomUUID(), "title", "wifi một máy", "description", "có thể chờ"));

        return json.readTree(mvc.perform(post(BASE).with(as("owner", "assistant-a", "requester"))
                        .contentType("application/json").content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode success(TriageRequest.Envelope envelope) {
        var response = TriageResponses.success(envelope);
        response.withObject("result")
                .put("category", "NETWORK").put("priority", "LOW").put("suggestedGroup", "IT-NETWORK");
        var explanation = response.withObject("explanation");
        explanation.put("impact", "I1").put("urgency", "U2").put("reasonCode", "CLASSIFIED");
        explanation.putArray("matchedRules")
                .add("CAT-NET-001").add("IMPACT-USER-001").add("URGENCY-DEFERRED-001").add("PRI-001");
        response.withObject("runtime").put("rulesVersion", "0.3.0");
        return response;
    }

    private static RequestPostProcessor as(String subject, String workspace, String... roles) {
        return jwt().jwt(token -> token.subject(subject).claim("groups", Arrays.stream(roles)
                .map(role -> "/workspaces/" + workspace + "/" + role).toList()));
    }

    private static String body(boolean guidance) {
        return "{\"contractVersion\":\"0.1.0\",\"includeGuidance\":" + guidance + "}";
    }

    private static String route(JsonNode ticket) {
        return BASE + "/" + ticket.path("id").asText() + "/assistant/assess";
    }
}
