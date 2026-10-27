package vn.haui.dx.apps.servicedesk.intake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.haui.dx.apps.servicedesk.clarification.*;
import vn.haui.dx.apps.servicedesk.intelligence.*;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.context.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.intelligence.api.*;
import vn.haui.dx.hpdi.intelligence.integration.ClassificationTransport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"serviceDesk.intake.new-tickets=true", "serviceDesk.intake.true-wait=true", "serviceDesk.context.enabled=true",
        "serviceDesk.context.mode=ASSISTED", "serviceDesk.contextual-assessment.enabled=true",
        "serviceDesk.classifier.mode=ASSISTED", "serviceDesk.classifier.provider=QWEN_LORA",
        "service-desk.background.scheduled=false", "service-desk.background.workspaces=d2-model"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "REAL_MODEL_BASE_URL", matches = "http://intelligence-classification:8003")
class RealQwenIntakeProof {
    private static final String BASE = "/api/apps/servicedesk/v1/workspaces/d2-model/tickets";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired BackgroundTicketAssessment worker;
    @Autowired TicketClarificationService clarifications;
    @MockBean ContextProcessingGate rights;
    @MockBean ClassifierProcessingGate modelRights;
    @org.springframework.boot.test.mock.mockito.SpyBean ClassificationTransport transport;
    @MockBean BackgroundProcessing background;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        registry.add("spring.datasource.username", () -> "workflow");
        registry.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
        registry.add("intelligence.classification.base-url", () -> System.getenv("REAL_MODEL_BASE_URL"));
        registry.add("intelligence.classification.token-file", () -> System.getenv("REAL_MODEL_TOKEN_FILE"));
        registry.add("serviceDesk.classifier.model-digest", () -> System.getenv("REAL_MODEL_DIGEST"));
        registry.add("serviceDesk.classifier.policy-digest", () -> System.getenv("REAL_MODEL_POLICY_DIGEST"));
        registry.add("serviceDesk.contextual-assessment.policy-digest", () -> {
            var mapper = new ObjectMapper();
            return new TicketAssessmentInputAssembler(mapper, new AssessmentPolicy(mapper), new ContextPolicyCatalog(mapper),
                    new ClassifierConfiguration.Settings(ClassifierConfiguration.Mode.ASSISTED, "QWEN_LORA",
                            System.getenv("REAL_MODEL_DIGEST"), System.getenv("REAL_MODEL_POLICY_DIGEST"), "", "")).policyDigest();
        });
    }

    @BeforeEach
    void configureSyntheticPrincipalAndIndependentGrants() {
        when(rights.verify(anyString(), anyString())).thenReturn("synthetic-context-grant");
        when(rights.verifyReadiness(anyString(), anyString(), anyString())).thenReturn("synthetic-context-grant");
        when(rights.verifyContextual(anyString(), anyString(), anyString(), anyString())).thenReturn("synthetic-context-grant");
        when(modelRights.verifyAssessment(anyString(), anyString())).thenReturn("synthetic-model-grant");
        doAnswer(call -> {
            runAs("synthetic-worker", "assessment-worker", call.getArgument(1));
            return null;
        }).when(background).run(eq("d2-model"), any(Runnable.class));

    }

    @Test
    void realModelFinalIsStoredOnceAndHumanReviewUsesThatGeneration() throws Exception {
        var ticket = create("Máy 15 phòng 1304 không lên nguồn");
        assertEquals("FINAL_ASSESSMENT_QUEUED", evaluate(ticket).path("status").asText());
        assertTrue(worker.processOne());
        var state = read(ticket);
        assertEquals("REVIEW_RELEASED", state.path("status").asText(), state.toPrettyString());
        assertEquals("HARDWARE", state.path("finalAssessment").path("ai07").path("category").asText());
        assertTrue(state.path("finalAssessment").path("ai07").path("confidence").isNull());
        for (int index = 0; index < 3; index++) assertEquals(state, read(ticket));
        assertFalse(worker.processOne());
        verify(transport, times(1)).observe(any());
        assertEquals(1L, jdbc.queryForObject("select count(*) from servicedesk_final_attempt where ticket_id=? and state='DONE'", Long.class, id(ticket)));
        mvc.perform(post(path(ticket) + "/intake/manual-release").with(as("coordinator", "coordinator")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("expectedContextRevision", 0, "expectedVersion", state.path("version").asLong(),
                        "operationId", UUID.randomUUID(), "reason", "Synthetic real model handoff")))).andExpect(status().isOk());
        var released = read(ticket);
        mvc.perform(post(path(ticket) + "/review").with(as("coordinator", "coordinator")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("category", "HARDWARE", "priority", "MEDIUM", "reason", "Synthetic human review",
                        "expectedContextRevision", 0, "expectedIntakeVersion", released.path("version").asLong())))).andExpect(status().isOk());
        assertEquals("REVIEWED", read(ticket).path("status").asText());
        assertEquals(1L, jdbc.queryForObject("select count(*) from servicedesk_ai_feedback where ticket_id=?", Long.class, id(ticket)));
        verify(transport, times(1)).observe(any());
    }

    private JsonNode create(String title) throws Exception {
        return body(mvc.perform(post(BASE).with(as("owner", "requester")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("clientRequestId", UUID.randomUUID(), "title", title, "description", title))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    private JsonNode evaluate(JsonNode ticket) throws Exception {
        return body(mvc.perform(post(path(ticket) + "/intake/evaluate").with(as("owner", "requester")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    private JsonNode read(JsonNode ticket) throws Exception {
        return body(mvc.perform(get(path(ticket) + "/intake").with(as("owner", "requester")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    private void append(JsonNode ticket, long revision, List<ClarificationRequest.Answer> answers) throws Exception {
        mvc.perform(post(path(ticket) + "/clarifications").with(as("owner", "requester")).contentType("application/json")
                .content(json.writeValueAsString(new ClarificationRequest("ticket-clarification@0.1.0", UUID.randomUUID(), revision, answers))))
                .andExpect(status().isCreated());
    }
    private JsonNode body(String value) throws Exception { return json.readTree(value); }
    private static UUID id(JsonNode ticket) { return UUID.fromString(ticket.path("id").asText()); }
    private static String path(JsonNode ticket) { return BASE + "/" + id(ticket); }
    private static RequestPostProcessor as(String subject, String role) {
        return jwt().jwt(token -> token.subject(subject).claim("groups", List.of("/workspaces/d2-model/" + role)));
    }
    private static void runAs(String subject, String role, Runnable operation) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("synthetic-only").header("alg", "none")
                .subject(subject).claim("groups", List.of("/workspaces/d2-model/" + role)).build()));
        SecurityContextHolder.setContext(context);
        try { operation.run(); } finally { SecurityContextHolder.setContext(previous); }
    }
}
