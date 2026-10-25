package vn.haui.dx.apps.servicedesk.assistance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.haui.dx.hpdi.intelligence.api.AssistanceInput;
import vn.haui.dx.hpdi.intelligence.integration.HttpAssistanceTransport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Core/PostgreSQL/private Python; identity fixtures are not gateway acceptance. */
@SpringBootTest
@AutoConfigureMockMvc
class TicketAssistancePythonE2E {
    private static final String BASE = "/api/apps/servicedesk/v1/workspaces/assist-a/tickets";
    private static final String CORE = "/api/core/v1/workspaces/assist-a";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        for (String name : List.of("FILE_TEST_JDBC_URL", "FILE_TEST_DB_PASSWORD",
                "INTELLIGENCE_ASSISTANCE_BASE_URL", "INTELLIGENCE_ASSISTANCE_TOKEN")) {
            if (System.getenv(name) == null || System.getenv(name).isBlank()) {
                throw new IllegalStateException("Missing integration setting: " + name);
            }
        }

        if (!System.getenv("FILE_TEST_JDBC_URL").endsWith("/file_storage")) {
            throw new IllegalStateException("Use a disposable file_storage database");
        }

        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("dx.storage.root", () -> Path.of(System.getProperty("java.io.tmpdir"), "assist-" + UUID.randomUUID()).toString());
        properties.add("dx.storage.allow-unscanned-upload", () -> "true");
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
        properties.add("serviceDesk.assistance.enabled", () -> "true");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockBean ServiceDeskAssistanceConfiguration.Settings settings;
    @SpyBean HttpAssistanceTransport transport;

    private UUID ticketId;
    private UUID metadataId;

    private RequestPostProcessor as(String subject, String workspace, String... roles) {
        return jwt().jwt(token -> token.subject(subject).claim("groups", Arrays.stream(roles)
            .map(role -> "/workspaces/" + workspace + "/" + role).toList()));
    }

    @BeforeEach
    void prepareActualCoreSourceAndTicket() throws Exception {
        var file = new MockMultipartFile("file", "synthetic.txt", "text/plain",
            "Verify the device connection.".getBytes(StandardCharsets.UTF_8));
        var uploaded = json.readTree(mvc.perform(multipart(CORE + "/files").file(file)
            .with(as("curator", "assist-a", "agent")))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        var metadata = json.readTree(mvc.perform(post(CORE + "/metadata")
            .with(as("curator", "assist-a", "agent")).contentType("application/json")
            .content(json.writeValueAsString(Map.of(
                "title", "Synthetic connection guide", "source", "synthetic:ai06", "version", "1",
                "usageRights", "synthetic-test-only", "fileId", uploaded.path("id").asText()))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        metadataId = UUID.fromString(metadata.path("id").asText());

        var ticket = json.readTree(mvc.perform(post(BASE).with(as("owner", "assist-a", "requester"))
            .contentType("application/json").content(json.writeValueAsString(Map.of(
                "clientRequestId", UUID.randomUUID(), "title", "Synthetic request", "description", "Private ticket content"))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        ticketId = UUID.fromString(ticket.path("id").asText());

        var pin = new AssistanceInput.SourcePin(metadataId, "1", metadata.path("checksum").asText(), "synthetic-test-only");
        when(settings.enabled()).thenReturn(true);
        when(settings.catalogs()).thenReturn(Map.of("assist-a", List.of(pin)));
        clearInvocations(transport);
    }

    private String path() {
        return BASE + "/" + ticketId + "/assistance";
    }

    private long[] counts() {
        return new long[] {
            jdbc.queryForObject("select count(*) from servicedesk_ticket", Long.class),
            jdbc.queryForObject("select count(*) from act_ru_task", Long.class),
            jdbc.queryForObject("select count(*) from audit_event", Long.class)
        };
    }

    @Test
    void actualCoreAndPythonReturnExactQuotationWithNoBusinessMutation() throws Exception {
        long[] before = counts();
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return call.callRealMethod();
        }).when(transport).assist(any());

        String body = mvc.perform(post(path()).with(as("owner", "assist-a", "requester"))
            .contentType("application/json").content("{\"question\":\"device connection\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.answer").value("Verify the device connection. [C1]"))
            .andExpect(jsonPath("$.citations[0].displayTitle").value("Synthetic connection guide"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(metadataId.toString()));
        assertFalse(body.contains("Private ticket content"));
        assertArrayEquals(before, counts());
        verify(transport).assist(any());
    }

    @Test
    void foreignActorOrWorkspaceCannotInvokePrivateRuntime() throws Exception {
        mvc.perform(post(path()).with(as("other-user", "assist-a", "requester"))
            .contentType("application/json").content("{\"question\":\"connection\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(post(path()).with(as("owner", "assist-b", "requester"))
            .contentType("application/json").content("{\"question\":\"connection\"}"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(transport);
    }

    @ParameterizedTest
    @CsvSource({"version,409", "checksum,409", "rights,409", "removed,404", "ticket,404"})
    void actualCoreChangesDuringRealPythonCallSuppressWholeAnswer(String mutation, int expected) throws Exception {
        doAnswer(call -> {
            JsonNode result = (JsonNode) call.callRealMethod();
            assertEquals("SUCCESS", result.path("status").asText());

            // Test-only authoritative state changes after real inference. Core
            // production metadata remains immutable through its public API.
            switch (mutation) {
                case "version" -> jdbc.update("update metadata set version='2' where id=?", metadataId);
                case "checksum" -> jdbc.update("update metadata set checksum=? where id=?", "0".repeat(64), metadataId);
                case "rights" -> jdbc.update("update metadata set usage_rights='withdrawn' where id=?", metadataId);
                case "removed" -> jdbc.update("delete from metadata where id=?", metadataId);
                case "ticket" -> jdbc.update("update servicedesk_ticket set creator='other-owner' where id=?", ticketId);
            }

            return result;
        }).when(transport).assist(any());

        mvc.perform(post(path()).with(as("owner", "assist-a", "requester"))
            .contentType("application/json").content("{\"question\":\"device connection\"}"))
            .andExpect(status().is(expected))
            .andExpect(jsonPath("$.answer").doesNotExist())
            .andExpect(jsonPath("$.citations").doesNotExist());
    }
}
