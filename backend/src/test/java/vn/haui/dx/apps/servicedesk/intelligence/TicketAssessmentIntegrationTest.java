package vn.haui.dx.apps.servicedesk.intelligence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

import vn.haui.dx.hpdi.intelligence.api.ClassificationObservation;
import vn.haui.dx.hpdi.intelligence.integration.ClassificationTransport;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(
        properties = {
            "serviceDesk.classifier.mode=ASSISTED",
            "serviceDesk.assessment.enabled=true",
            "serviceDesk.classifier.provider=SYNTHETIC_ACCEPTANCE",
            "serviceDesk.classifier.model-digest=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "serviceDesk.classifier.policy-digest=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        })
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class TicketAssessmentIntegrationTest {
    private static final String BASE = "/api/apps/servicedesk/v1/workspaces/classifier-a/tickets";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add(
                "serviceDesk.assessment.policy-bundle-digest",
                () ->
                        new vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy(
                                        new ObjectMapper())
                                .bundleDigest());
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add(
                "dx.storage.root",
                () ->
                        Path.of(
                                        System.getProperty("java.io.tmpdir"),
                                        "classifier-test-" + UUID.randomUUID())
                                .toString());
        properties.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:1/jwks");
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private ClassificationTransport transport;
    @MockBean private ClassifierProcessingGate processing;

    @BeforeEach
    void configure() {
        when(processing.verify("classifier-a")).thenReturn("SYNTHETIC_GRANT_FOR_TEST_ONLY");
        when(transport.observe(any()))
                .thenAnswer(
                        call -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            return vote();
                        });
    }

    @Test
    void realAuthorizationPrecedesAnyModelProcessing() throws Exception {
        var ticket = create();
        mvc.perform(post(route(ticket)).contentType("application/json").content(body()))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post(route(ticket))
                                .with(as("other", "classifier-a"))
                                .contentType("application/json")
                                .content(body()))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post(route(ticket))
                                .with(as("foreign", "classifier-b"))
                                .contentType("application/json")
                                .content(body()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(transport, processing);
    }

    @Test
    void authorizedModelProposalIsIndependentFromUrgencyAndReadOnly() throws Exception {
        var ticket = create();
        long ticketsBefore =
                jdbc.queryForObject("select count(*) from servicedesk_ticket", Long.class);
        long auditBefore = jdbc.queryForObject("select count(*) from audit_event", Long.class);
        long tasksBefore = jdbc.queryForObject("select count(*) from act_ru_task", Long.class);
        mvc.perform(
                        post(route(ticket))
                                .with(as("owner", "classifier-a"))
                                .contentType("application/json")
                                .content(body()))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Cache-Control",
                                        org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.classification.mainCategory").value("ACCOUNT"))
                .andExpect(jsonPath("$.disposition.needsReview").value(true))
                .andExpect(jsonPath("$.disposition.operationalAuthority").value(false))
                .andExpect(jsonPath("$.assessment.priority").doesNotExist());
        assertEquals(
                ticketsBefore,
                jdbc.queryForObject("select count(*) from servicedesk_ticket", Long.class));
        assertEquals(
                tasksBefore, jdbc.queryForObject("select count(*) from act_ru_task", Long.class));
        assertEquals(
                auditBefore, jdbc.queryForObject("select count(*) from audit_event", Long.class));
        String current =
                mvc.perform(
                                get(BASE + "/" + ticket.path("id").asText())
                                        .with(as("owner", "classifier-a")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertEquals(ticket, json.readTree(current));
    }

    @Test
    void snapshotDriftNeverLeaksPartialProposal() throws Exception {
        var ticket = create();
        when(transport.observe(any()))
                .thenAnswer(
                        call -> {
                            jdbc.update(
                                    "update servicedesk_ticket set title=? where id=?",
                                    "Changed during inference",
                                    UUID.fromString(ticket.path("id").asText()));
                            return vote();
                        });
        mvc.perform(
                        post(route(ticket))
                                .with(as("owner", "classifier-a"))
                                .contentType("application/json")
                                .content(body()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.classification").doesNotExist());
    }

    private ClassificationObservation vote() {
        return new ClassificationObservation(
                "PROPOSE",
                "ACCOUNT",
                "a".repeat(64),
                "b".repeat(64),
                "UNVERIFIED_BY_MODEL",
                "NOT_AVAILABLE",
                false);
    }

    private JsonNode create() throws Exception {
        String payload =
                json.writeValueAsString(
                        Map.of(
                                "clientRequestId",
                                UUID.randomUUID(),
                                "title",
                                "Wi-Fi yêu cầu xác thực tài khoản",
                                "description",
                                "Thiếu urgency"));
        return json.readTree(
                mvc.perform(
                                post(BASE)
                                        .with(as("owner", "classifier-a"))
                                        .contentType("application/json")
                                        .content(payload))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private static RequestPostProcessor as(String subject, String workspace) {
        return jwt().jwt(
                        token ->
                                token.subject(subject)
                                        .claim(
                                                "groups",
                                                Arrays.asList(
                                                        "/workspaces/"
                                                                + workspace
                                                                + "/requester")));
    }

    private static String body() {
        return "{\"contractVersion\":\"ticket-intelligence-assessment@0.2.0\"}";
    }

    private static String route(JsonNode ticket) {
        return BASE + "/" + ticket.path("id").asText() + "/assistant/assessment";
    }
}
