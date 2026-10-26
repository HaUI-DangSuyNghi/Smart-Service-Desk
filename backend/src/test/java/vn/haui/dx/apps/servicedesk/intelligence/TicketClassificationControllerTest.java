package vn.haui.dx.apps.servicedesk.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import vn.haui.dx.hpdi.common.api.CapabilityException;

import java.util.UUID;

class TicketClassificationControllerTest {
    @RestControllerAdvice
    static class Errors {
        @ExceptionHandler(CapabilityException.class)
        ResponseEntity<Void> invalid() {
            return ResponseEntity.badRequest().build();
        }
    }

    @Test
    void classifierModeDefaultsToDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(ClassifierConfiguration.class)
                .run(
                        context ->
                                assertThat(
                                                context.getBean(
                                                                ClassifierConfiguration.Settings
                                                                        .class)
                                                        .mode())
                                        .isEqualTo(ClassifierConfiguration.Mode.DISABLED));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "null",
                "[]",
                "",
                "{\"contractVersion\":\"old\"}",
                "{\"contractVersion\":\"model-primary-assessment@0.1.0\",\"priority\":\"HIGH\"}",
                "{\"contractVersion\":\"model-primary-assessment@0.1.0\",\"contractVersion\":\"model-primary-assessment@0.1.0\"}",
                "{\"contractVersion\":\"model-primary-assessment@0.1.0\"} {}"
            })
    void callerCannotSelectModelOrSupplyClassificationControls(String body) throws Exception {
        var classifier = mock(TicketClassificationOrchestrator.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(
                                new TicketClassificationController(classifier, new ObjectMapper()))
                        .setControllerAdvice(new Errors())
                        .build();
        mvc.perform(
                        post("/api/apps/servicedesk/v1/workspaces/test/tickets/"
                                        + UUID.randomUUID()
                                        + "/assistant/classify")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(classifier);
    }
}
