package vn.haui.dx.apps.servicedesk.assistance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.apps.servicedesk.intelligence.LocalModelRuntime;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.Actor;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.intelligence.api.*;

class ApiTicketAssistanceTest {
    private final UUID id = UUID.randomUUID();
    private final TicketService tickets = mock(TicketService.class);
    private final HumanContext human = mock(HumanContext.class);
    private final AdvisoryAssistanceCapability advisory = mock(AdvisoryAssistanceCapability.class);
    private final GroundedAssistanceCapability grounded = mock(GroundedAssistanceCapability.class);
    private final Actor actor = new Actor("owner", "lab-a", Set.of("requester"), Set.of());
    private final Ticket ticket = new Ticket(id, "lab-a", "owner", UUID.randomUUID(), "Máy không lên nguồn",
            "Cần thi trong 15 phút", Instant.now(), null);
    private TicketAssistanceService service;

    @BeforeEach void setup() {
        var environment = new MockEnvironment(); environment.setActiveProfiles("local-dev");
        var runtime = new LocalModelRuntime("LOCAL_DEV", List.of("lab-a"), "http://intelligence-classification:8003", environment);
        service = new TicketAssistanceService(tickets, grounded,
                new ServiceDeskAssistanceConfiguration.Settings(true, Map.of(), "local-api"), null, human, advisory, runtime);
        when(tickets.get("lab-a", id)).thenReturn(ticket);
        when(human.currentActor("lab-a")).thenReturn(actor);
        when(advisory.advise(anyString(), anyMap())).thenReturn(new AssistanceOutcome(
                "0.2.0", UUID.randomUUID().toString(), "SUCCESS", "Kiểm tra cáp nguồn bên ngoài.", List.of(), true, "MODEL_ADVICE"));
    }

    @Test void sendsOnlyVisibleTicketAndQuestionThenRechecksAccess() {
        var result = service.assist("lab-a", id, "Tôi nên làm gì?");
        assertEquals("MODEL_ADVICE", result.reasonCode()); assertTrue(result.citations().isEmpty());
        var order = inOrder(tickets, advisory);
        order.verify(tickets).get("lab-a", id);
        order.verify(advisory).advise("Tôi nên làm gì?", Map.of("title", ticket.title(), "description", ticket.description()));
        order.verify(tickets).get("lab-a", id);
        verifyNoInteractions(grounded);
        verifyNoMoreInteractions(tickets);
    }

    @Test void deniedTicketNeverReachesProvider() {
        when(tickets.get("lab-a", id)).thenThrow(CapabilityException.notFound("hidden"));
        assertThrows(CapabilityException.class, () -> service.assist("lab-a", id, "help"));
        verifyNoInteractions(advisory);
    }

    @Test void unsupportedWorkspaceNeverReachesProvider() {
        when(tickets.get("lab-b", id)).thenReturn(new Ticket(id, "lab-b", "owner", ticket.clientRequestId(),
                ticket.title(), ticket.description(), ticket.createdAt(), null));
        assertThrows(CapabilityException.class, () -> service.assist("lab-b", id, "help"));
        verifyNoInteractions(advisory);
    }

    @Test void revokedAccessSuppressesAnswer() {
        when(tickets.get("lab-a", id)).thenReturn(ticket).thenThrow(CapabilityException.forbidden("revoked"));
        assertThrows(CapabilityException.class, () -> service.assist("lab-a", id, "help"));
    }

    @Test void changedActorSuppressesAnswer() {
        when(human.currentActor("lab-a")).thenReturn(actor).thenReturn(new Actor("other", "lab-a", Set.of("requester"), Set.of()));
        assertThrows(CapabilityException.class, () -> service.assist("lab-a", id, "help"));
    }

    @Test void changedTicketSuppressesAnswer() {
        when(tickets.get("lab-a", id)).thenReturn(ticket).thenReturn(new Ticket(id, "lab-a", "owner", ticket.clientRequestId(),
                ticket.title(), "changed", ticket.createdAt(), null));
        assertThrows(CapabilityException.class, () -> service.assist("lab-a", id, "help"));
    }

    @Test void questionLimitPreventsUpstreamCall() {
        assertThrows(CapabilityException.class, () -> service.assist("lab-a", id, "x".repeat(2001)));
        verifyNoInteractions(advisory);
    }

    @Test void apiModeCannotBeActivatedInExternalRuntime() {
        var configuration = new ServiceDeskAssistanceConfiguration();
        assertThrows(IllegalStateException.class, () -> configuration.configuredServiceDeskAssistanceSettings(
                true, "local-api", "", new com.fasterxml.jackson.databind.ObjectMapper(), LocalModelRuntime.external()));
    }
    @Test void automaticAdviceAppliesToAnotherVisibleWorkspaceWithoutNewConfiguration() {
        var environment = new MockEnvironment(); environment.setActiveProfiles("local-dev");
        var runtime = new LocalModelRuntime("LOCAL_DEV", List.of("auto"), "http://intelligence-classification:8003", environment);
        var automatic = new TicketAssistanceService(tickets, grounded,
                new ServiceDeskAssistanceConfiguration.Settings(true, Map.of(), "local-api"), null, human, advisory, runtime);
        when(tickets.get("lab-b", id)).thenReturn(new Ticket(id, "lab-b", "owner", ticket.clientRequestId(), ticket.title(), ticket.description(), ticket.createdAt(), null));
        when(human.currentActor("lab-b")).thenReturn(new Actor("owner", "lab-b", Set.of("requester"), Set.of()));
        assertTrue(automatic.availableFor("lab-b"));
        assertEquals("MODEL_ADVICE", automatic.assist("lab-b", id, "help").reasonCode());
        verify(tickets, times(2)).get("lab-b", id);
    }
}
