package vn.haui.dx.apps.roomrequest;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RoomRequestPythonE2E {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        for (String name : List.of("FILE_TEST_JDBC_URL", "FILE_TEST_DB_PASSWORD", "INTELLIGENCE_TRIAGE_BASE_URL", "INTELLIGENCE_TRIAGE_TOKEN"))
            if (System.getenv(name) == null || System.getenv(name).isBlank()) throw new IllegalStateException("Missing E2E setting: " + name);
        p.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        p.add("spring.datasource.username", () -> "workflow");
        p.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        p.add("dx.storage.root", () -> Path.of(System.getProperty("java.io.tmpdir"), "room-e2e-" + UUID.randomUUID()).toString());
        p.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void secondAppUsesRealUnchangedHpdiAndGenericPythonWithoutServiceDesk() throws Exception {
        assertTrue(Boolean.getBoolean("dx.proof.second-domain"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("vn.haui.dx.apps.servicedesk.Ticket"));
        String base = "/api/apps/roomrequest/v1/workspaces/lab-a/requests";
        var owner = jwt().jwt(token -> token.subject("room-owner").claim("groups", List.of("/workspaces/lab-a/requester")));
        var room = json.readTree(mvc.perform(post(base).with(owner).contentType("application/json").content("{\"room\":\"A-101\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String route = base + "/" + room.path("id").asText() + "/suggestion";
        mvc.perform(post(route).with(owner)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.runtime.profileVersion").value("room-request@0.3.0"))
            .andExpect(jsonPath("$.result.category").value("BOOKING"))
            .andExpect(jsonPath("$.result.priority").value("NORMAL"));
        mvc.perform(post(route).with(jwt().jwt(token -> token.subject("other").claim("groups", List.of("/workspaces/lab-a/requester")))))
            .andExpect(status().isNotFound());
        mvc.perform(post(route)).andExpect(status().isUnauthorized());
    }
}
