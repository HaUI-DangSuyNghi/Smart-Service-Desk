package vn.haui.dx.core.workflow.internal;

import org.junit.jupiter.api.Test;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.common.PageRequest;
import vn.haui.dx.core.workflow.api.TaskExecution;
import vn.haui.dx.core.workflow.api.WorkflowService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowExecutionCompatibilityTest {
    @Test void executionPoliciesCannotOptInAnotherRegistrationOrUnknownTask() {
        var repository = mock(org.flowable.engine.RepositoryService.class);
        for (var declaration : java.util.List.of(java.util.Map.of("foreign", java.util.Set.of("work")),
                java.util.Map.of("owned", java.util.Set.of("unknown")))) {
            vn.haui.dx.core.workflow.api.ProcessRegistration registration = new vn.haui.dx.core.workflow.api.ProcessRegistration() {
                public java.util.List<vn.haui.dx.core.workflow.api.ProcessPolicy> policies() {
                    return java.util.List.of(new vn.haui.dx.core.workflow.api.ProcessPolicy("owned",
                        java.util.Set.of(vn.haui.dx.core.identity.api.WorkspaceRole.REQUESTER), java.util.Set.of(),
                        java.util.Map.of("work", java.util.Set.of("done"))));
                }
                public java.util.Map<String, java.util.Set<String>> executionTasks() { return declaration; }
            };
            assertThrows(IllegalArgumentException.class, () -> new ProcessRegistry(java.util.List.of(registration), repository));
        }
        verifyNoInteractions(repository);
    }

    @Test void additiveExecutionMethodsHaveFailClosedDefaultsForLegacyImplementations() throws Exception {
        assertTrue(WorkflowService.class.getMethod("execute", String.class, String.class,
            TaskExecution.Action.class, String.class, String.class).isDefault());
        assertTrue(WorkflowService.class.getMethod("queue", String.class, String.class, PageRequest.class).isDefault());
        var legacy = mock(WorkflowService.class, CALLS_REAL_METHODS);
        assertEquals(ErrorCode.CONFLICT, assertThrows(CoreException.class,
            () -> legacy.execute("lab-a", "task", TaskExecution.Action.START, null, null)).code());
        assertEquals(ErrorCode.CONFLICT, assertThrows(CoreException.class,
            () -> legacy.queue("lab-a", null, PageRequest.of(0, 20))).code());
    }
}
