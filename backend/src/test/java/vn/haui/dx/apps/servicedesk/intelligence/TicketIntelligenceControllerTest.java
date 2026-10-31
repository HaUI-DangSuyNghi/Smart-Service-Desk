package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.haui.dx.hpdi.common.api.CapabilityException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketIntelligenceControllerTest {
    private final TicketIntelligenceOrchestrator assistant = mock(TicketIntelligenceOrchestrator.class);
    private final UUID id = UUID.randomUUID();
    private MockMvc mvc;

    @RestControllerAdvice
    static class Errors {
        @ExceptionHandler(CapabilityException.class)
        ResponseEntity<Void> invalid() {
            return ResponseEntity.badRequest().build();
        }
    }

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new TicketIntelligenceController(assistant, new ObjectMapper()))
                .setControllerAdvice(new Errors())
                .build();
    }

    @Test
    void acceptsAutoAssessAndDisablesCaching() throws Exception {
        mvc.perform(post(path()).contentType("application/json")
                        .content("{\"contractVersion\":\"0.1.0\",\"includeGuidance\":false}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(assistant).assess(eq("lab-a"), eq(id),
                eq(new TicketAssessmentRequest("0.1.0", null, false)));
    }

    @Test
    void endpointIsOffByDefaultAndRequiresExplicitFlag() {
        var context = new ApplicationContextRunner()
                .withUserConfiguration(TicketIntelligenceController.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(TicketIntelligenceOrchestrator.class, () -> assistant);

        context.run(application -> org.assertj.core.api.Assertions.assertThat(application)
                .doesNotHaveBean(TicketIntelligenceController.class));
        context.withPropertyValues("serviceDesk.assistant.enabled=true")
                .run(application -> org.assertj.core.api.Assertions.assertThat(application)
                        .hasSingleBean(TicketIntelligenceController.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "null", "[]", "",
        "{\"contractVersion\":\"0.1.0\",\"includeGuidance\":false,\"model\":\"override\"}",
        "{\"contractVersion\":\"0.1.0\",\"includeGuidance\":false,\"question\":null}",
        "{\"contractVersion\":\"0.1.0\",\"includeGuidance\":false,\"question\":\" \"}",
        "{\"contractVersion\":\"0.1.0\",\"includeGuidance\":false,\"includeGuidance\":true}",
        "{\"contractVersion\":\"0.2.0\",\"includeGuidance\":false}",
        "{\"contractVersion\":\"0.1.0\",\"includeGuidance\":\"false\"}",
        "{\"contractVersion\":\"0.1.0\",\"includeGuidance\":false} {}"
    })
    void rejectsMalformedAndUntrustedOverrides(String body) throws Exception {
        mvc.perform(post(path()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assistant);
    }

    @Test
    void rejectsOversizedRequestsAndQuestions() throws Exception {
        for (String body : new String[] {
            "x".repeat(16385),
            "{\"contractVersion\":\"0.1.0\",\"includeGuidance\":true,\"question\":\"" + "x".repeat(2001) + "\"}"
        }) {
            mvc.perform(post(path()).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(assistant);
    }

    private String path() {
        return "/api/apps/servicedesk/v1/workspaces/lab-a/tickets/" + id + "/assistant/assess";
    }
}
