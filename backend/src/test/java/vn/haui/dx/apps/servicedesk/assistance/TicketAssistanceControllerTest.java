package vn.haui.dx.apps.servicedesk.assistance;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.intelligence.api.AssistanceOutcome;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketAssistanceControllerTest {
    private final TicketAssistanceService service = mock(TicketAssistanceService.class);
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
        mvc = MockMvcBuilders.standaloneSetup(new TicketAssistanceController(service, new ObjectMapper()))
            .setControllerAdvice(new Errors()).build();
        when(service.assist(anyString(), any(), anyString())).thenReturn(new AssistanceOutcome(
            "0.1.0", UUID.randomUUID().toString(), "INSUFFICIENT_EVIDENCE", null, List.of(), true,
            "INSUFFICIENT_EVIDENCE"));
    }

    private String path() {
        return "/api/apps/servicedesk/v1/workspaces/scope-a/tickets/" + id + "/assistance";
    }

    @Test
    void publicContractAcceptsQuestionOnlyAndDisablesCaching() throws Exception {
        mvc.perform(post(path()).contentType("application/json").content("{\"question\":\"device connection\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.status").value("INSUFFICIENT_EVIDENCE"))
            .andExpect(jsonPath("$.citations").isEmpty());

        verify(service).assist("scope-a", id, "device connection");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "[]", "null", "", "{\"question\":null}",
        "{\"question\":\"a\",\"question\":\"b\"}",
        "{\"question\":\"a\",\"model\":\"override\"}",
        "{\"question\":\"a\",\"sourceIds\":[]}",
        "{\"question\":\"a\",\"subject\":\"other-user\"}",
        "{\"question\":\"a\"} {}"
    })
    void rejectsUntrustedOverridesAndMalformedJson(String body) throws Exception {
        mvc.perform(post(path()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void oversizedBodyNeverReachesUseCase() throws Exception {
        mvc.perform(post(path()).contentType("application/json").content("x".repeat(16385)))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void defaultOffAndRealDataCannotBeEnabledByOnlyFlippingFlag() {
        var config = new ServiceDeskAssistanceConfiguration();
        var json = new ObjectMapper();

        org.junit.jupiter.api.Assertions.assertFalse(config.serviceDeskAssistanceSettings(false, "disabled", "", json).enabled());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
            () -> config.serviceDeskAssistanceSettings(true, "production", "{}", json));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
            () -> config.serviceDeskAssistanceSettings(true, "synthetic-reference", "{}", json));
    }
}
