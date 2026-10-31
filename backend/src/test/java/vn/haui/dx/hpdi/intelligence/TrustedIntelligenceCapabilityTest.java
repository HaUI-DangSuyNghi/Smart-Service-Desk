package vn.haui.dx.hpdi.intelligence;

import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.Actor;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.intelligence.api.TriageInput;
import vn.haui.dx.hpdi.intelligence.integration.IntelligenceTransport;
import vn.haui.dx.hpdi.intelligence.integration.TriageResponses;
import vn.haui.dx.hpdi.intelligence.integration.TriageValidator;
import vn.haui.dx.hpdi.intelligence.internal.TrustedIntelligenceCapability;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrustedIntelligenceCapabilityTest {
    private final ObjectMapper json = new ObjectMapper();
    private final HumanContext human = mock(HumanContext.class);

    @Test void derivesIndependentContextAndCorrelationForAnArbitraryExternalProfile() {
        when(human.currentActor("lab-a")).thenReturn(new Actor("verified-subject", "lab-a", Set.of("specialist"), Set.of()));
        var ids = new java.util.HashSet<String>();
        IntelligenceTransport transport = envelope -> {
            assertEquals("verified-subject", envelope.trustedContext().subject());
            assertEquals("lab-a", envelope.trustedContext().workspaceId());
            assertEquals("lab-a", envelope.request().context().workspaceId());
            assertEquals("fake-domain", envelope.request().profile());
            assertEquals("1.0", envelope.profileVersion());
            assertDoesNotThrow(() -> UUID.fromString(envelope.request().requestId()));
            assertTrue(ids.add(envelope.request().requestId()));
            return TriageResponses.success(envelope);
        };
        var capability = new TrustedIntelligenceCapability(human, transport, new TriageValidator(json), json);
        var input = new TriageInput("fake-domain", "1.0", "Stored title", "Stored description");
        assertEquals("SUCCESS", capability.suggest("lab-a", input).attributes().get("status"));
        assertEquals("SUCCESS", capability.suggest("lab-a", input).attributes().get("status"));
        assertEquals(2, ids.size());
    }

    @Test void deniedOrMismatchedActorNeverReachesTransport() {
        var transport = mock(IntelligenceTransport.class);
        var capability = new TrustedIntelligenceCapability(human, transport, new TriageValidator(json), json);
        when(human.currentActor("denied")).thenThrow(CapabilityException.forbidden("Workspace membership required"));
        var input = new TriageInput("fake-domain", "1.0", "Title", "Description");
        assertThrows(CapabilityException.class, () -> capability.suggest("denied", input));
        when(human.currentActor("lab-a")).thenReturn(new Actor("actor", "lab-b", Set.of(), Set.of()));
        assertThrows(CapabilityException.class, () -> capability.suggest("lab-a", input));
        verifyNoInteractions(transport);
    }
}
