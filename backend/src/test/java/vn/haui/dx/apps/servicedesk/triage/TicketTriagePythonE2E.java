package vn.haui.dx.apps.servicedesk.triage;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TicketTriagePythonE2E {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        for (String name : List.of("FILE_TEST_JDBC_URL", "FILE_TEST_DB_PASSWORD", "INTELLIGENCE_TRIAGE_BASE_URL", "INTELLIGENCE_TRIAGE_TOKEN"))
            if (System.getenv(name) == null || System.getenv(name).isBlank()) throw new IllegalStateException("Required E2E setting: " + name);
        if (!System.getenv("FILE_TEST_JDBC_URL").endsWith("/file_storage")) throw new IllegalStateException("Use a disposable file_storage database");
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("dx.storage.root", () -> Path.of(System.getProperty("java.io.tmpdir"), "triage-python-" + UUID.randomUUID()).toString());
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void publicApplicationCallsActualPythonThroughHttpAdapter() throws Exception {
        String base = "/api/apps/servicedesk/v1/workspaces/lab-a/tickets";
        var identity = jwt().jwt(token -> token.subject("synthetic-e2e-owner").claim("groups", List.of("/workspaces/lab-a/requester")));
        var ticket = json.readTree(mvc.perform(post(base).with(identity).contentType("application/json")
            .content(json.writeValueAsString(Map.of("clientRequestId", UUID.randomUUID(), "title", "Network support",
                "description", "Synthetic network support request"))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(post(base + "/" + ticket.path("id").asText() + "/triage").with(identity))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.runtime.profileVersion").value("service-desk@0.2.0"));
    }
}
