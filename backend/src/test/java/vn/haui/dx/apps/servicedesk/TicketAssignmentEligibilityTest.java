package vn.haui.dx.apps.servicedesk;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketAssignmentEligibilityTest {
    private final MemberDirectory directory = mock(MemberDirectory.class);
    private final AgentDutyService duty = mock(AgentDutyService.class);
    private final TicketAssignmentEligibility eligibility = new TicketAssignmentEligibility(directory, duty);

    @Test void categoryMappingPreservesManualOtherAndAccountEstimate() {
        assertEquals("hardware", TicketAssignmentEligibility.supportGroup(TicketReview.Category.HARDWARE));
        assertEquals("network", TicketAssignmentEligibility.supportGroup(TicketReview.Category.NETWORK));
        assertEquals("software", TicketAssignmentEligibility.supportGroup(TicketReview.Category.SOFTWARE));
        assertEquals("sysadmin", TicketAssignmentEligibility.supportGroup(TicketReview.Category.ACCOUNT));
        assertNull(TicketAssignmentEligibility.supportGroup(TicketReview.Category.OTHER));
        assertEquals(20, TicketReview.defaultMinutes(TicketReview.Category.ACCOUNT, TicketReview.Priority.HIGH));
    }

    @Test void creatorAndMissingOrRevokedMembershipFailClosed() {
        assertThrows(CapabilityException.class, () -> eligibility.require("lab-a", TicketReview.Category.HARDWARE, "agent", "agent"));
        verifyNoInteractions(directory, duty);
        assertThrows(CapabilityException.class, () -> eligibility.require("lab-a", TicketReview.Category.HARDWARE, "owner", "agent"));
        verifyNoInteractions(duty);
    }

    @Test void dutyIsCheckedAndEligibilityIsRevalidatedRatherThanCached() {
        var agent = new WorkspaceMember("agent", "agent", null, Set.of("agent"));
        when(directory.currentAgents("lab-a", "hardware")).thenReturn(List.of(agent), List.of(agent), List.of());
        assertThrows(CapabilityException.class, () -> eligibility.require("lab-a", TicketReview.Category.HARDWARE, "owner", "agent"));
        when(duty.onDuty("lab-a", "agent")).thenReturn(true);
        assertDoesNotThrow(() -> eligibility.require("lab-a", TicketReview.Category.HARDWARE, "owner", "agent"));
        assertThrows(CapabilityException.class, () -> eligibility.require("lab-a", TicketReview.Category.HARDWARE, "owner", "agent"));
        verify(directory, times(3)).currentAgents("lab-a", "hardware");
        assertThrows(CapabilityException.class, () -> eligibility.require("lab-b", TicketReview.Category.HARDWARE, "owner", "agent"));
    }
}
