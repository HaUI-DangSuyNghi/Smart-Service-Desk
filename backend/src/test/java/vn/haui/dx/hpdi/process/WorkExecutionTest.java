package vn.haui.dx.hpdi.process;

import org.junit.jupiter.api.Test;
import vn.haui.dx.core.workflow.api.TaskExecution;
import vn.haui.dx.core.workflow.api.WorkflowService;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.process.api.WorkExecution;
import vn.haui.dx.hpdi.process.internal.CoreProcessCapabilities;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkExecutionTest {
    @Test void anApplicationCannotOptInAnotherApplicationsProcessOrUnknownTask() {
        for (var declaration : java.util.List.of(java.util.Map.of("foreign", java.util.Set.of("work")),
                java.util.Map.of("owned", java.util.Set.of("unknown")))) {
            vn.haui.dx.hpdi.process.api.ProcessContribution contribution = new vn.haui.dx.hpdi.process.api.ProcessContribution() {
                public java.util.List<vn.haui.dx.hpdi.process.api.ProcessDefinition> definitions() {
                    return java.util.List.of(new vn.haui.dx.hpdi.process.api.ProcessDefinition("owned", java.util.Set.of("requester"),
                        java.util.Set.of(), java.util.Map.of("work", java.util.Set.of("done"))));
                }
                public java.util.Map<String, java.util.Set<String>> executionTasks() { return declaration; }
            };
            assertThrows(IllegalArgumentException.class,
                () -> new vn.haui.dx.hpdi.process.internal.ContributionRegistry(java.util.List.of(contribution)));
        }
    }

    @Test void workTaskOptInIsContributedByApplicationsAndDefaultsToDisabled() {
        vn.haui.dx.hpdi.process.api.ProcessContribution domain = new vn.haui.dx.hpdi.process.api.ProcessContribution() {
            public java.util.List<vn.haui.dx.hpdi.process.api.ProcessDefinition> definitions() {
                return java.util.List.of(new vn.haui.dx.hpdi.process.api.ProcessDefinition("second-domain", java.util.Set.of("requester"),
                    java.util.Set.of(), java.util.Map.of("fulfil", java.util.Set.of("done"))));
            }
            public java.util.Map<String, java.util.Set<String>> executionTasks() {
                return java.util.Map.of("second-domain", java.util.Set.of("fulfil"));
            }
        };
        var registry = new vn.haui.dx.hpdi.process.internal.ContributionRegistry(java.util.List.of(domain));
        assertEquals(java.util.Set.of("fulfil"), registry.executionTasks().get("second-domain"));
        vn.haui.dx.hpdi.process.api.ProcessContribution legacy = domain::definitions;
        assertTrue(new vn.haui.dx.hpdi.process.internal.ContributionRegistry(java.util.List.of(legacy)).executionTasks().isEmpty());
    }

    @Test void pauseReasonsAreHpdiPolicyAndInvalidRequestsNeverReachCore() {
        var workflow = mock(WorkflowService.class);
        var capability = new CoreProcessCapabilities(workflow);
        for (String reason : new String[] { null, "", "UNRECOGNIZED" })
            assertThrows(CapabilityException.class, () -> capability.execute("lab-a", "task", WorkExecution.Action.PAUSE, reason, null));
        for (String note : new String[] { null, "", " " })
            assertThrows(CapabilityException.class, () -> capability.execute("lab-a", "task", WorkExecution.Action.PAUSE, "OTHER", note));
        assertThrows(CapabilityException.class, () -> capability.execute("lab-a", "task", null, null, null));
        assertThrows(CapabilityException.class, () -> capability.queue("lab-a", null, null));
        verifyNoInteractions(workflow);
    }

    @Test void capabilityPreservesCoreExecutionValuesWithoutExposingCoreModels() {
        var workflow = mock(WorkflowService.class);
        var task = new vn.haui.dx.core.workflow.api.TaskView("task", "instance", "fulfil", "Fulfil", java.util.List.of("lab-a:agent"),
            "worker", java.time.Instant.EPOCH, "second-domain", "request");
        when(workflow.execute("lab-a", "task", TaskExecution.Action.PAUSE, "OTHER", "External dependency"))
            .thenReturn(new TaskExecution(task, TaskExecution.State.PENDING, 75, null, 125_000, null, "OTHER", "External dependency"));
        var result = new CoreProcessCapabilities(workflow).execute("lab-a", "task", WorkExecution.Action.PAUSE, "OTHER", "External dependency");
        assertEquals("PENDING", result.workState());
        assertEquals(125_000, result.workedMillis());
        assertEquals(75, result.priority());
        assertEquals("External dependency", result.pendingNote());
        assertEquals("second-domain", result.task().processKey());
    }
}
