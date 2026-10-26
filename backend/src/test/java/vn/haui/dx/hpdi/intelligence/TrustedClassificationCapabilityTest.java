package vn.haui.dx.hpdi.intelligence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.Actor;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.intelligence.api.ClassificationInput;
import vn.haui.dx.hpdi.intelligence.api.ClassificationObservation;
import vn.haui.dx.hpdi.intelligence.integration.ClassificationTransport;
import vn.haui.dx.hpdi.intelligence.internal.TrustedClassificationCapability;

import java.util.List;
import java.util.Map;
import java.util.Set;

class TrustedClassificationCapabilityTest {
    @Test
    void secondDomainUsesNeutralPortAndRechecksActor() {
        var human = mock(HumanContext.class);
        var transport = mock(ClassificationTransport.class);
        var actor = new Actor("owner", "rooms", Set.of("requester"), Set.of());
        when(human.currentActor("rooms")).thenReturn(actor);
        var input =
                new ClassificationInput(
                        Map.of("purpose", "Book a quiet room"),
                        List.of("MEETING", "STUDY"),
                        "a".repeat(64),
                        "b".repeat(64));
        var observation =
                new ClassificationObservation(
                        "PROPOSE",
                        "STUDY",
                        "a".repeat(64),
                        "b".repeat(64),
                        "UNVERIFIED_BY_MODEL",
                        "NOT_AVAILABLE",
                        false);
        when(transport.observe(input)).thenReturn(observation);
        var capability = new TrustedClassificationCapability(human, transport);
        assertSame(observation, capability.observe("rooms", input));
        verify(transport).observe(input);
        when(transport.observe(input))
                .thenAnswer(
                        call -> {
                            when(human.currentActor("rooms"))
                                    .thenReturn(new Actor("other", "rooms", Set.of(), Set.of()));
                            return observation;
                        });
        assertThrows(CapabilityException.class, () -> capability.observe("rooms", input));
    }
}
