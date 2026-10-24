package vn.haui.dx.hpdi.process;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.haui.dx.core.workflow.api.*;
import vn.haui.dx.hpdi.process.internal.CoreProcessCapabilities;
import vn.haui.dx.hpdi.process.internal.ContributionRegistry;
import vn.haui.dx.hpdi.process.api.*;
import vn.haui.dx.hpdi.common.api.PageRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoreProcessCapabilitiesTest {
    @Test void inboxCarriesBusinessCorrelationAndAssignmentWithoutHistoryAccess() {
        var core = mock(WorkflowService.class);
        var at = Instant.parse("2026-01-01T00:00:00Z");
        var task = new TaskView("task", "instance", "review", "Review", List.of("lab-a:agent"), null,
            at, "arbitrary-domain", "request-1");
        when(core.inbox(eq("lab-a"), any(), any())).thenReturn(
            new vn.haui.dx.core.common.Page<>(List.of(task), 0, 20, 1));
        var result = new CoreProcessCapabilities(core).list("lab-a", WorkInbox.Scope.CANDIDATE, new PageRequest(0, 20));
        assertEquals("arbitrary-domain", result.items().get(0).processKey());
        assertEquals("request-1", result.items().get(0).businessKey());
        assertEquals(at, result.items().get(0).createdAt());
        verify(core, never()).history(anyString(), anyString());
    }

    @Test void startedByMeMapsCoreSummariesWithoutEngineTypes() {
        var core = mock(WorkflowService.class);
        var at = Instant.parse("2026-01-01T00:00:00Z");
        when(core.startedByMe(eq("lab-a"), any())).thenReturn(new vn.haui.dx.core.common.Page<>(
            List.of(new vn.haui.dx.core.workflow.api.ProcessSummary("instance", "arbitrary-domain", "request-1", "owner-1", at, null,
                List.of("review"))), 0, 20, 1));
        var result = new CoreProcessCapabilities(core).startedByMe("lab-a", new PageRequest(0, 20));
        var item = result.items().get(0);
        assertEquals("instance", item.id());
        assertEquals(List.of("review"), item.activeTaskKeys());
        assertNull(item.endedAt());
        assertEquals("owner-1", item.initiator());
        assertEquals(1, result.total());
    }

    @Test void contributionsAcceptNewRolesWithoutEditingCapability() {
        ProcessContribution domain = () -> List.of(new ProcessDefinition("new-domain", java.util.Set.of("specialist"),
            java.util.Set.of(), java.util.Map.of("review", java.util.Set.of("accepted"))));
        var policy = new ContributionRegistry(List.of(domain)).policies().get(0);
        assertEquals("specialist", policy.starterRoles().iterator().next().value());
        assertEquals("new-domain", policy.processKey());
    }
}
