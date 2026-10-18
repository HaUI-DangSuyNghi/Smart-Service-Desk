package vn.haui.dx.apps.roomrequest;
import java.util.*;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="FILE_TEST_JDBC_URL",matches=".+/file_storage")
class RoomRequestIntegrationTest {
    private static final String BASE="/api/apps/roomrequest/v1/workspaces/lab-a/requests";
    private static final Path STORAGE=Path.of(System.getProperty("java.io.tmpdir"),"room-proof-"+UUID.randomUUID());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("FILE_TEST_JDBC_URL"));
        p.add("spring.datasource.username",()->"workflow");
        p.add("spring.datasource.password",()->System.getenv("FILE_TEST_DB_PASSWORD"));
        p.add("dx.storage.root",STORAGE::toString); p.add("dx.storage.allow-unscanned-upload",()->"true");
        p.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",()->"https://room.test/issuer");
        p.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",()->"http://127.0.0.1:9/no-jwks");
    }
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    private RequestPostProcessor as(String subject,String workspace,String role) {
        return jwt().jwt(token->token.subject(subject).claim("groups",List.of("/workspaces/"+workspace+"/"+role)));
    }
    private JsonNode body(ResultActions action) throws Exception { return json.readTree(action.andReturn().getResponse().getContentAsByteArray()); }
    private JsonNode create() throws Exception {
        return body(mvc.perform(post(BASE).with(as("room-owner","lab-a","requester")).contentType("application/json")
            .content("{\"room\":\"A-101\"}")).andExpect(status().isCreated()));
    }
    @Test void requestUsesUnchangedHumanProcessInboxTimelineAndJournal() throws Exception {
        if (Boolean.getBoolean("dx.proof.second-domain")) {
            assertThrows(ClassNotFoundException.class,()->Class.forName("vn.haui.dx.apps.servicedesk.Ticket"));
            assertNull(jdbc.queryForObject("select to_regclass('servicedesk_ticket')::text",String.class));
        }
        var request=create(); String id=request.path("id").asText();
        var progress=body(mvc.perform(get(BASE+"/"+id+"/progress").with(as("room-owner","lab-a","requester"))).andExpect(status().isOk()));
        String task=progress.path("currentTasks").get(0).path("id").asText();
        String hpdi="/api/hpdi/v1/workspaces/lab-a";
        mvc.perform(get(hpdi+"/inbox").with(as("room-reviewer","lab-a","coordinator")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[?(@.id == '"+task+"')].processKey").value("room-request"));
        mvc.perform(post(hpdi+"/tasks/"+task+"/claim").with(as("room-reviewer","lab-a","coordinator")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.businessKey").value(id));
        mvc.perform(post(hpdi+"/tasks/"+task+"/complete").with(as("room-reviewer","lab-a","coordinator"))
            .contentType("application/json").content("{\"expectedTaskKey\":\"approve\",\"outcome\":\"approved\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(get(BASE+"/"+id+"/progress").with(as("room-owner","lab-a","requester")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ended").value(true));
        assertEquals(1L,jdbc.queryForObject("select count(*) from audit_event where resource=? and action='roomrequest.request.create'",Long.class,id));
        mvc.perform(get(BASE+"/"+id).with(as("other","lab-a","requester"))).andExpect(status().isNotFound());
        mvc.perform(get(BASE+"/"+id).with(as("foreign","lab-b","coordinator"))).andExpect(status().isForbidden());
    }
    @Test void requestResolvesSharedKnowledgeAndRejectsForeignKnowledgeBeforePersistence() throws Exception {
        byte[] content="Room booking guide".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var uploaded=body(mvc.perform(multipart("/api/core/v1/workspaces/lab-a/files")
            .file(new MockMultipartFile("file","room-guide.md","text/markdown",content))
            .with(as("room-agent","lab-a","agent"))).andExpect(status().isCreated()));
        var metadata=body(mvc.perform(post("/api/core/v1/workspaces/lab-a/metadata").with(as("room-agent","lab-a","agent"))
            .contentType("application/json").content(json.writeValueAsString(Map.of("title","Room guide","source","src:room",
                "version","v1","usageRights","CC0","fileId",uploaded.path("id").asText()))))
            .andExpect(status().isCreated()));
        String payload=json.writeValueAsString(Map.of("room","A-102","knowledgeId",metadata.path("id").asText()));
        mvc.perform(post(BASE).with(as("room-owner","lab-a","requester")).contentType("application/json").content(payload))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.knowledgeId").value(metadata.path("id").asText()));
        mvc.perform(post(BASE.replace("lab-a","lab-b")).with(as("foreign","lab-b","requester"))
            .contentType("application/json").content(payload)).andExpect(status().isNotFound());
        assertEquals(0L,jdbc.queryForObject("select count(*) from roomrequest_request where workspace='lab-b'",Long.class));
    }
    @Test void intelligenceUnavailabilityIsExplicitAndCannotBypassRequestVisibility() throws Exception {
        var request=create();String route=BASE+"/"+request.path("id").asText()+"/suggestion";
        mvc.perform(post(route).with(as("room-owner","lab-a","requester")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DEGRADED"))
            .andExpect(jsonPath("$.needsReview").value(true));
        mvc.perform(post(route).with(as("other","lab-a","requester"))).andExpect(status().isNotFound());
        mvc.perform(post(route)).andExpect(status().isUnauthorized());
    }
}
