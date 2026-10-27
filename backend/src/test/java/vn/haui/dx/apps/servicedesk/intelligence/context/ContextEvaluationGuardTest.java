package vn.haui.dx.apps.servicedesk.intelligence.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.ProblemAssessor;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.Actor;
import vn.haui.dx.hpdi.human.api.HumanContext;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContextEvaluationGuardTest {
    private final TicketService tickets = mock(TicketService.class);
    private final ContextSnapshotAssembler snapshots = mock(ContextSnapshotAssembler.class);
    private final ContextProcessingGate processing = mock(ContextProcessingGate.class);
    private final HumanContext human = mock(HumanContext.class);
    private final ObjectMapper json = new ObjectMapper();
    private final ContextReadinessPolicy policy = new ContextReadinessPolicy(json, new ProblemAssessor(new AssessmentPolicy(json)));
    private final UUID id = UUID.randomUUID();
    private final Actor owner = new Actor("owner", "lab", Set.of("requester"), Set.of());

    @Test
    void actorRevokedDuringEvaluationDiscardsWholeResult() {
        var guard = prepare(ContextIntelligenceConfiguration.Mode.ASSISTED);
        assertThrows(CapabilityException.class, () -> guard.evaluate("lab", id, session -> {
            when(human.currentActor("lab")).thenReturn(new Actor("owner", "lab", Set.of(), Set.of()));
            return "must not be returned";
        }));
    }

    @Test
    void sourceRevisionChangedAfterEvaluationDiscardsWholeResult() {
        var guard = prepare(ContextIntelligenceConfiguration.Mode.ASSISTED);
        assertThrows(CapabilityException.class, () -> guard.evaluate("lab", id, session -> {
            when(snapshots.read("lab", id)).thenReturn(snapshot("b".repeat(64)));
            return "stale";
        }));
    }

    @Test
    void shadowPublishesNoReadinessOrSourceDigest() {
        var guard = prepare(ContextIntelligenceConfiguration.Mode.SHADOW);
        var service = new ContextReadinessService(guard,
                new ContextSlotExtractor(new ContextPolicyCatalog(json), new AssessmentPolicy(json)), policy);
        var result = service.assess("lab", id);
        assertNull(result.overall());
        assertNull(result.contextDigest());
        assertTrue(result.questions().isEmpty());
        assertTrue(result.tasks().isEmpty());
    }

    private ContextEvaluationGuard prepare(ContextIntelligenceConfiguration.Mode mode) {
        when(tickets.get("lab", id)).thenReturn(new Ticket(id, "lab", "owner", UUID.randomUUID(),
                "title", "description", Instant.EPOCH, null));
        when(human.currentActor("lab")).thenReturn(owner);
        when(snapshots.read("lab", id)).thenReturn(snapshot("a".repeat(64)));
        when(processing.verifyReadiness(eq("lab"), anyString(), anyString())).thenReturn("grant");
        return new ContextEvaluationGuard(tickets, snapshots, human, processing,
                new ContextIntelligenceConfiguration.Settings(true, mode, "", "", ""), policy);
    }

    private ContextSnapshot snapshot(String digest) {
        return new ContextSnapshot("lab", id, 0, List.of(new ContextSnapshot.Source("title", 0, "title", null, false)), digest);
    }
}
