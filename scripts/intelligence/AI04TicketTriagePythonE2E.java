package vn.haui.dx.apps.servicedesk.triage;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.context.annotation.Import(vn.haui.dx.core.workflow.internal.WorkflowStateFixture.class)
@SpringBootTest
@AutoConfigureMockMvc
class AI04TicketTriagePythonE2E {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        for (String name : List.of("FILE_TEST_JDBC_URL", "FILE_TEST_DB_PASSWORD", "INTELLIGENCE_TRIAGE_BASE_URL", "INTELLIGENCE_TRIAGE_TOKEN", "AI04_SCENARIO"))
            if (System.getenv(name) == null || System.getenv(name).isBlank()) throw new IllegalStateException("Missing setting: " + name);
        if (!System.getenv("FILE_TEST_JDBC_URL").endsWith("/file_storage")) throw new IllegalStateException("Disposable file_storage required");
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("dx.storage.root", () -> Path.of(System.getProperty("java.io.tmpdir"), "ai04-" + UUID.randomUUID()).toString());
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired vn.haui.dx.core.workflow.internal.WorkflowStateFixture engineState;

    private RequestPostProcessor as(String subject, String workspace, String role) {
        return jwt().jwt(token -> token.subject(subject).claim("groups", List.of("/workspaces/" + workspace + "/" + role)));
    }

    private long[] counts() {
        return java.util.stream.LongStream.concat(java.util.stream.LongStream.of(
            jdbc.queryForObject("select count(*) from servicedesk_ticket", Long.class)),
            java.util.Arrays.stream(engineState.counts())).toArray();
    }

    @Test void realTransportPreservesAuthorizationStateAndManualFallback() throws Exception {
        String workspace = "ai04-" + UUID.randomUUID();
        String base = "/api/apps/servicedesk/v1/workspaces/" + workspace + "/tickets";
        var owner = as("synthetic-ai04-owner", workspace, "requester");
        var ticket = json.readTree(mvc.perform(post(base).with(owner).contentType("application/json")
            .content(json.writeValueAsString(Map.of("clientRequestId", UUID.randomUUID(), "title", "Network support",
                "description", "Synthetic network support request"))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String route = base + "/" + ticket.path("id").asText();
        long[] before = counts();
        mvc.perform(post(route + "/triage")).andExpect(status().isUnauthorized());
        mvc.perform(post(route + "/triage").with(as("other-owner", workspace, "requester"))).andExpect(status().isNotFound());
        mvc.perform(post(route + "/triage").with(as("foreign", "other-workspace", "requester"))).andExpect(status().isForbidden());
        mvc.perform(post(route.replace(workspace, "other-workspace") + "/triage")
            .with(as("foreign", "other-workspace", "agent"))).andExpect(status().isNotFound());
        String scenario = System.getenv("AI04_SCENARIO");
        int expected = switch (scenario) {
            case "success" -> 200;
            case "wrong-token", "malformed" -> 502;
            case "unavailable" -> 503;
            case "timeout" -> 504;
            default -> throw new IllegalStateException("Unknown scenario");
        };
        var result = mvc.perform(post(route + "/triage").with(owner)).andExpect(status().is(expected));
        if (expected == 200) {
            result.andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.runtime.profileVersion").value("service-desk@0.2.0"))
                .andExpect(jsonPath("$.workspaceId").value(workspace))
                .andExpect(jsonPath("$.contractVersion").value("0.1.0"));
        } else {
            String code = switch (scenario) {
                case "wrong-token" -> "INTELLIGENCE_INTEGRATION_FAILED";
                case "malformed" -> "INTELLIGENCE_CONTRACT_FAILED";
                case "timeout" -> "INTELLIGENCE_TIMEOUT";
                default -> "INTELLIGENCE_UNAVAILABLE";
            };
            result.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.result").doesNotExist());
        }
        assertArrayEquals(before, counts());
        mvc.perform(get(route).with(owner)).andExpect(status().isOk()).andExpect(content().json(ticket.toString()));
        JsonNode progress = json.readTree(mvc.perform(get(route + "/progress").with(owner))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String task = progress.path("currentTasks").get(0).path("id").asText();
        String taskRoute = "/api/core/v1/workspaces/" + workspace + "/instances/"
            + ticket.path("process").path("processInstanceId").asText() + "/tasks/" + task;
        var agent = as("synthetic-ai04-agent", workspace, "agent");
        mvc.perform(post(taskRoute + "/claim").with(agent)).andExpect(status().isOk());
        mvc.perform(post(taskRoute + "/complete").with(agent).contentType("application/json")
            .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}")).andExpect(status().isNoContent());
    }
}
