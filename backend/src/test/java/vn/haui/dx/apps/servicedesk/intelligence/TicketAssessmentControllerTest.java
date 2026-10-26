package vn.haui.dx.apps.servicedesk.intelligence;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import vn.haui.dx.hpdi.common.api.CapabilityException;

import java.util.UUID;

class TicketAssessmentControllerTest {
    @RestControllerAdvice
    static class Errors {
        @ExceptionHandler(CapabilityException.class)
        ResponseEntity<Void> invalid() {
            return ResponseEntity.badRequest().build();
        }
    }

    @Test
    void boundedBodyIsRejectedBeforeInference() throws Exception {
        callerCannotSelectModelOrSupplyClassificationControls(" ".repeat(1025));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "null",
                "[]",
                "",
                "{\"contractVersion\":\"old\"}",
                "{\"contractVersion\":\"ticket-intelligence-assessment@0.2.0\",\"priority\":\"HIGH\"}",
                "{\"contractVersion\":\"ticket-intelligence-assessment@0.2.0\",\"contractVersion\":\"ticket-intelligence-assessment@0.2.0\"}",
                "{\"contractVersion\":\"ticket-intelligence-assessment@0.2.0\"} {}"
            })
    void callerCannotSelectModelOrSupplyClassificationControls(String body) throws Exception {
        var classifier = mock(TicketAssessmentOrchestrator.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(
                                new TicketAssessmentController(classifier, new ObjectMapper()))
                        .setControllerAdvice(new Errors())
                        .build();
        mvc.perform(
                        post("/api/apps/servicedesk/v1/workspaces/test/tickets/"
                                        + UUID.randomUUID()
                                        + "/assistant/assessment")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(classifier);
    }
}
