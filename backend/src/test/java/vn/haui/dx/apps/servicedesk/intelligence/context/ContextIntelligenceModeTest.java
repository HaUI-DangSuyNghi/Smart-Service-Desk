package vn.haui.dx.apps.servicedesk.intelligence.context;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.duplicate.DuplicateCandidateProvider;
import vn.haui.dx.apps.servicedesk.intelligence.duplicate.DuplicateDecisionEngine;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.Actor;
import vn.haui.dx.hpdi.human.api.HumanContext;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class ContextIntelligenceModeTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ContextPolicyCatalog policy = new ContextPolicyCatalog(json);
    private final TicketService tickets = mock(TicketService.class);
    private final ContextSnapshotAssembler snapshots = mock(ContextSnapshotAssembler.class);
    private final ContextProcessingGate processing = mock(ContextProcessingGate.class);
    private final HumanContext human = mock(HumanContext.class);
    private final DuplicateCandidateProvider candidates = mock(DuplicateCandidateProvider.class);
    private final ContextSlotExtractor extractor = spy(new ContextSlotExtractor(policy, new AssessmentPolicy(json)));
    private final UUID id = UUID.randomUUID();
    private final Actor owner = new Actor("owner", "lab", Set.of("requester"), Set.of());

    @Test
    void disabledAndPromotedModesNeverProcessContent() {
        prepare();
        for (var mode : List.of(ContextIntelligenceConfiguration.Mode.DISABLED,
                ContextIntelligenceConfiguration.Mode.PROMOTED)) {
            assertThrows(CapabilityException.class, () -> service(true, mode).enrich("lab", id, "R10_DUPLICATE"));
        }
        assertThrows(CapabilityException.class, () -> service(false,
                ContextIntelligenceConfiguration.Mode.ASSISTED).enrich("lab", id, "R10_DUPLICATE"));
        verifyNoInteractions(processing, snapshots, candidates, extractor);
    }

    @Test
    void shadowNeverPublishesContextOrCandidateSuggestions() {
        prepare();
        var service = service(true, ContextIntelligenceConfiguration.Mode.SHADOW);
        var enrichment = service.enrich("lab", id, "R10_DUPLICATE");
        assertEquals("SHADOW", enrichment.status());
        assertTrue(enrichment.context().isEmpty());
        assertTrue(enrichment.enrichmentSignals().isEmpty());
        assertTrue(enrichment.questions().isEmpty());
        assertNull(enrichment.provenance());
        when(candidates.windowStart()).thenReturn(Instant.EPOCH);
        when(candidates.retrieve(eq("lab"), eq(id), any())).thenReturn(
                new DuplicateCandidateProvider.Batch(List.of(), 0, false, "synthetic-digest"));
        var duplicates = service.duplicates("lab", id);
        assertTrue(duplicates.candidates().isEmpty());
        assertEquals(DuplicateDecisionEngine.Decision.INSUFFICIENT_EVIDENCE, duplicates.decision());
        assertNull(duplicates.provenance());
        assertFalse(duplicates.disposition().autoMergeAllowed());
    }

    @Test
    void actorChangeDuringProcessingFailsClosed() {
        prepare();
        when(human.currentActor("lab")).thenReturn(owner).thenReturn(
                new Actor("different", "lab", Set.of("requester"), Set.of()));
        assertThrows(CapabilityException.class, () -> service(true,
                ContextIntelligenceConfiguration.Mode.ASSISTED).enrich("lab", id, "R10_DUPLICATE"));
    }

    @Test
    void actorRevokedDuringFinalSnapshotReadCannotPublishReviewerOutput() {
        prepare();
        when(human.currentActor("lab")).thenReturn(owner, owner,
                new Actor("owner", "lab", Set.of(), Set.of()));
        assertThrows(CapabilityException.class, () -> service(true,
                ContextIntelligenceConfiguration.Mode.ASSISTED).enrich("lab", id, "R10_DUPLICATE"));
    }

    private void prepare() {
        when(tickets.get("lab", id)).thenReturn(new Ticket(
                id, "lab", "owner", UUID.randomUUID(), "Máy 15 phòng 1304 không lên nguồn",
                "Mô tả tổng hợp", Instant.EPOCH, null));
        when(human.currentActor("lab")).thenReturn(owner);
        when(processing.verify("lab", ContextProcessingGate.OWN_TICKET)).thenReturn("grant");
        when(snapshots.read("lab", id)).thenReturn(new ContextSnapshot("lab", id, 0,
                List.of(new ContextSnapshot.Source("title", 0, "Máy 15 phòng 1304 không lên nguồn", null, false)), "digest"));
    }

    private ContextIntelligenceService service(boolean enabled, ContextIntelligenceConfiguration.Mode mode) {
        var settings = new ContextIntelligenceConfiguration.Settings(enabled, mode, policy.bundleDigest(), "", "");
        return new ContextIntelligenceService(tickets, snapshots, extractor, new EnrichmentSignalPlanner(policy),
                settings, policy, processing, human, candidates, new DuplicateDecisionEngine());
    }
}
