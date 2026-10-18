package vn.haui.dx.apps.servicedesk.triage;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class TicketTriageIntegrationTest {
    private static final String BASE = "/api/apps/servicedesk/v1/workspaces/lab-a/tickets";
    private static final Path STORAGE = Path.of(System.getProperty("java.io.tmpdir"), "triage-test-" + UUID.randomUUID());

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("dx.storage.root", STORAGE::toString);
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockBean IntelligenceTriageClient intelligence;

    @BeforeEach void fake() {
        when(intelligence.triage(any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return TriageResponses.success(invocation.getArgument(0));
        });
    }

    private RequestPostProcessor as(String subject, String workspace, String... roles) {
        return jwt().jwt(token -> token.subject(subject).claim("groups", Arrays.stream(roles)
            .map(role -> "/workspaces/" + workspace + "/" + role).toList()));
    }

    private JsonNode create() throws Exception {
        return json.readTree(mvc.perform(post(BASE).with(as("triage-owner", "lab-a", "requester"))
            .contentType("application/json").content(json.writeValueAsString(Map.of("clientRequestId", UUID.randomUUID(),
                "title", "Stored network support", "description", "Persisted synthetic description"))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private long[] counts() {
        return List.of("servicedesk_ticket", "process_scope", "act_hi_procinst", "act_ru_task", "audit_event")
            .stream().mapToLong(table -> jdbc.queryForObject("select count(*) from " + table, Long.class)).toArray();
    }

    @Test void authorizationAndVisibilityRunBeforeAi() throws Exception {
        var ticket = create();
        String route = BASE + "/" + ticket.path("id").asText() + "/triage";
        mvc.perform(post(route)).andExpect(status().isUnauthorized());
        mvc.perform(post(route).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(post(route).with(as("foreign", "lab-b", "requester"))).andExpect(status().isForbidden());
        mvc.perform(post(route).with(as("hidden", "lab-a", "requester"))).andExpect(status().isNotFound());
        mvc.perform(post(route).with(as("observer", "lab-a", "observer"))).andExpect(status().isForbidden());
        mvc.perform(post(route.replace("lab-a", "lab-b")).with(as("foreign", "lab-b", "agent"))).andExpect(status().isNotFound());
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/triage").with(as("triage-owner", "lab-a", "requester")))
            .andExpect(status().isNotFound());
        verifyNoInteractions(intelligence);
        for (String role : List.of("requester", "agent", "coordinator")) {
            String subject = role.equals("requester") ? "triage-owner" : "triage-" + role;
            mvc.perform(post(route).with(as(subject, "lab-a", role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        }
        verify(intelligence, times(3)).triage(argThat(request -> request.input().title().equals(ticket.path("title").asText())
            && request.input().description().equals(ticket.path("description").asText())
            && request.context().workspaceId().equals("lab-a")));
    }

    @Test void rejectsEveryNonemptyBodyIncludingEmptyObjectAndChunkedInput() throws Exception {
        String route = BASE + "/" + create().path("id").asText() + "/triage";
        for (String body : List.of("{}", " ", "null", "[]", "{\"title\":\"override\"}"))
            mvc.perform(post(route).with(as("triage-owner", "lab-a", "requester"))
                .header("Transfer-Encoding", "chunked").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post(BASE + "/bad-id/triage").with(as("triage-owner", "lab-a", "requester"))).andExpect(status().isBadRequest());
        verifyNoInteractions(intelligence);
    }

    @Test void successDegradedAndFailureNeverMutateTicketOrWorkflow() throws Exception {
        var ticket = create();
        String route = BASE + "/" + ticket.path("id").asText();
        long[] before = counts();
        mvc.perform(post(route + "/triage").with(as("triage-owner", "lab-a", "requester"))).andExpect(status().isOk());
        when(intelligence.triage(any())).thenAnswer(invocation -> TriageResponses.degraded(invocation.getArgument(0)));
        mvc.perform(post(route + "/triage").with(as("triage-owner", "lab-a", "requester")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DEGRADED"))
            .andExpect(jsonPath("$.needsReview").value(true));
        for (TriageFailure failure : List.of(TriageFailure.invalid(), TriageFailure.configuration(), TriageFailure.unavailable(), TriageFailure.timeout())) {
            doThrow(failure).when(intelligence).triage(any());
            mvc.perform(post(route + "/triage").with(as("triage-owner", "lab-a", "requester")))
                .andExpect(status().is(failure.status())).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value(failure.code()));
        }
        assertArrayEquals(before, counts());
        mvc.perform(get(route).with(as("triage-owner", "lab-a", "requester")))
            .andExpect(status().isOk()).andExpect(content().json(ticket.toString()));
        var progress = json.readTree(mvc.perform(get(route + "/progress").with(as("triage-owner", "lab-a", "requester")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String task = progress.path("currentTasks").get(0).path("id").asText();
        String taskRoute = "/api/core/v1/workspaces/lab-a/instances/" + ticket.path("process").path("processInstanceId").asText() + "/tasks/" + task;
        mvc.perform(post(taskRoute + "/claim").with(as("triage-handler", "lab-a", "agent"))).andExpect(status().isOk());
        mvc.perform(post(taskRoute + "/complete").with(as("triage-handler", "lab-a", "agent")).contentType("application/json")
            .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}")).andExpect(status().isNoContent());
    }

    @Test void malformedUpstreamIsAProblemNotASuggestion() throws Exception {
        String route = BASE + "/" + create().path("id").asText() + "/triage";
        when(intelligence.triage(any())).thenReturn(json.createObjectNode().put("confidence", 1));
        mvc.perform(post(route).with(as("triage-owner", "lab-a", "requester")))
            .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("INTELLIGENCE_CONTRACT_FAILED"));
    }
}
