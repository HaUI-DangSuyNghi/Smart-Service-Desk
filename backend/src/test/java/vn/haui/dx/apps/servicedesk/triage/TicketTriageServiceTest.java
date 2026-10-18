package vn.haui.dx.apps.servicedesk.triage;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.core.common.CoreException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketTriageServiceTest {
    @Test void usesOnlyPersistedInputAndValidatesFakeResponses() {
        var tickets = mock(TicketService.class);
        UUID id = UUID.randomUUID();
        var ticket = new Ticket(id, "lab-a", "owner", UUID.randomUUID(), "Stored title", "Stored description", Instant.now(), null);
        when(tickets.get("lab-a", id)).thenReturn(ticket);
        IntelligenceTriageClient client = request -> {
            assertEquals("Stored title", request.input().title());
            assertEquals("Stored description", request.input().description());
            assertEquals("lab-a", request.context().workspaceId());
            assertEquals("service-desk", request.profile());
            assertDoesNotThrow(() -> UUID.fromString(request.requestId()));
            return TriageResponses.success(request);
        };
        var service = new TicketTriageService(tickets, client, new TriageValidator(new ObjectMapper()));
        var first = service.triage("lab-a", id);
        assertNotEquals(first.get("requestId"), service.triage("lab-a", id).get("requestId"));
        verify(tickets, times(2)).get("lab-a", id);
        verifyNoMoreInteractions(tickets);
    }

    @Test void deniedReadNeverCallsAi() {
        var tickets = mock(TicketService.class);
        var client = mock(IntelligenceTriageClient.class);
        when(tickets.get(anyString(), any())).thenThrow(CoreException.notFound("Ticket not found"));
        var service = new TicketTriageService(tickets, client, new TriageValidator(new ObjectMapper()));
        assertThrows(CoreException.class, () -> service.triage("lab-a", UUID.randomUUID()));
        verifyNoInteractions(client);
    }
}
