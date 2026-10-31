package vn.haui.dx.apps.servicedesk.intelligence;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Isolated synthetic authorization only; never provisions a production processing grant. */
@SpringBootTest(
        properties = {
            "serviceDesk.classifier.mode=ASSISTED",
            "serviceDesk.classifier.provider=QWEN_SYNTHETIC_E2E",
            "serviceDesk.assessment.enabled=true"
        })
@AutoConfigureMockMvc
abstract class QwenAssessmentTestSupport {
    protected static final String WORKSPACE = "qwen-synthetic-e2e";
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @MockBean protected ClassifierProcessingGate processing;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry properties) {
        if (!"true".equals(System.getenv("AI07_REAL_QWEN_E2E"))) {
            throw new IllegalStateException("Explicit synthetic Qwen E2E opt-in is required");
        }
        properties.add("spring.datasource.url", () -> required("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> required("FILE_TEST_DB_PASSWORD"));
        properties.add(
                "serviceDesk.classifier.model-digest", () -> required("AI07_QWEN_MODEL_DIGEST"));
        properties.add(
                "serviceDesk.classifier.policy-digest", () -> required("AI07_QWEN_POLICY_DIGEST"));
        properties.add(
                "intelligence.classification.base-url", () -> required("AI07_QWEN_BASE_URL"));
        properties.add("intelligence.classification.token", () -> required("AI07_QWEN_TOKEN"));
        properties.add(
                "serviceDesk.assessment.policy-bundle-digest",
                () -> new AssessmentPolicy(new ObjectMapper()).bundleDigest());
        properties.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:1/jwks");
        properties.add(
                "dx.storage.root",
                () ->
                        Path.of(
                                        System.getProperty("java.io.tmpdir"),
                                        "ai07-qwen-e2e-" + UUID.randomUUID())
                                .toString());
    }

    protected String createSyntheticRoute() throws Exception {
        when(processing.verify(WORKSPACE)).thenReturn("SYNTHETIC_TEST_ONLY");
        when(processing.verifyAssessment(eq(WORKSPACE), anyString()))
                .thenReturn("SYNTHETIC_TEST_ONLY");
        String base = "/api/apps/servicedesk/v1/workspaces/" + WORKSPACE + "/tickets";
        var result =
                mvc.perform(
                                post(base)
                                        .with(actor())
                                        .contentType("application/json")
                                        .content(
                                                json.writeValueAsBytes(
                                                        Map.of(
                                                                "clientRequestId",
                                                                UUID.randomUUID(),
                                                                "title",
                                                                "không kết nối được Wi-Fi",
                                                                "description",
                                                                "14 máy trong phòng B305 không truy"
                                                                    + " cập được Internet; tiết học"
                                                                    + " bắt đầu sau 8 phút"))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse();
        return base
                + "/"
                + json.readTree(result.getContentAsByteArray()).path("id").asText()
                + "/assistant/assessment";
    }

    protected MockHttpServletResponse request(String route) throws Exception {
        return mvc.perform(
                        post(route)
                                .with(actor())
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsBytes(
                                                Map.of(
                                                        "contractVersion",
                                                        TicketAssessmentView.VERSION))))
                .andReturn()
                .getResponse();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor actor() {
        return jwt().jwt(
                        token ->
                                token.subject("synthetic-owner")
                                        .claim(
                                                "groups",
                                                List.of(
                                                        "/workspaces/"
                                                                + WORKSPACE
                                                                + "/requester")));
    }

    protected long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    protected static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + name);
        }
        return value;
    }
}
