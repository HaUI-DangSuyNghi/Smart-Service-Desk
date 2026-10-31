package vn.haui.dx.core;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.data.FileApi;
import vn.haui.dx.core.data.MetadataApi;
import vn.haui.dx.core.data.api.FileService;
import vn.haui.dx.core.data.api.MetadataService;
import vn.haui.dx.core.identity.api.*;
import vn.haui.dx.core.identity.internal.EndUserPermissionConfiguration;
import vn.haui.dx.core.workflow.WorkflowApi;
import vn.haui.dx.core.workflow.api.WorkflowService;
import vn.haui.dx.core.workflow.internal.ProcessRegistry;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CoreAuthorizationRegressionTest {
    private final WorkspaceAccess access = mock(WorkspaceAccess.class);
    private final WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
    private final CurrentUser user = mock(CurrentUser.class);
    private final MetadataService metadata = mock(MetadataService.class);
    private final FileService files = mock(FileService.class);
    private final WorkflowService workflow = mock(WorkflowService.class);
    private final ProcessRegistry processes = mock(ProcessRegistry.class);
    private MockMvc mvc;

    private static class Registry extends InterceptorRegistry {
        HandlerInterceptor[] interceptors() { return getInterceptors().toArray(HandlerInterceptor[]::new); }
    }

    @BeforeEach void setup() {
        when(access.current()).thenReturn(user);
        when(user.isMember("lab-a")).thenReturn(true);
        when(user.memberships()).thenReturn(Set.of(new Membership("lab-a", WorkspaceRole.AGENT)));
        var registry = new Registry();
        new EndUserPermissionConfiguration(new WorkspacePermissions(access, directory)).addInterceptors(registry);
        mvc = MockMvcBuilders.standaloneSetup(new MetadataApi(metadata), new FileApi(files, true),
            new WorkflowApi(workflow, processes))
            .addInterceptors(registry.interceptors()).setControllerAdvice(new Errors()).build();
    }

    private void grant(String... permissions) {
        doReturn(Map.of("agent",
            java.util.Arrays.stream(permissions).map(name -> new DirectoryPermission(name, null)).toList()))
            .when(directory).rolePermissions("lab-a");
    }

    @Test void genericStartDefaultsToDeniedWithoutExplicitSelfService() throws Exception {
        grant("request:create");
        var policy = new vn.haui.dx.core.workflow.api.ProcessPolicy("example", Set.of(WorkspaceRole.AGENT), Set.of(), Map.of());
        assertFalse(policy.selfService());
        when(processes.require("example")).thenReturn(policy);
        mvc.perform(post("/api/core/v1/workspaces/lab-a/processes/example/instances")
            .contentType("application/json").content("{\"businessKey\":\"example\",\"variables\":{\"selfService\":true}}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(workflow);
    }

    @Test void memberCannotReadDocumentsBySwitchingToCoreMetadata() throws Exception {
        mvc.perform(get("/api/core/v1/workspaces/lab-a/metadata")).andExpect(status().isForbidden());
        mvc.perform(get("/api/core/v1/workspaces/lab-a/metadata/00000000-0000-0000-0000-000000000001"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(metadata);
    }

    @Test void fileDescriptorAndBytesCannotBypassDocumentPermission() throws Exception {
        String path = "/api/core/v1/workspaces/lab-a/files/00000000-0000-0000-0000-000000000001";
        mvc.perform(get(path)).andExpect(status().isForbidden());
        mvc.perform(get(path + "/content")).andExpect(status().isForbidden());
        verifyNoInteractions(files);
    }

    @Test void previewPermissionDoesNotGrantOriginalFileDownload() throws Exception {
        grant("document:view");
        mvc.perform(get("/api/core/v1/workspaces/lab-a/files/00000000-0000-0000-0000-000000000001/content"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(files);
    }

    @Test void candidateCannotListOrClaimThroughCoreAfterPermissionRevoked() throws Exception {
        mvc.perform(get("/api/core/v1/workspaces/lab-a/tasks?scope=candidate")).andExpect(status().isForbidden());
        mvc.perform(post("/api/core/v1/workspaces/lab-a/instances/p/tasks/t/claim")).andExpect(status().isForbidden());
        verifyNoInteractions(workflow);
    }

    @Test void allowedReadsThenChangedDirectoryPolicyDeniesTheSameSignedRole() throws Exception {
        grant("document:view", "task:handle");
        when(metadata.search(anyString(), any(), any())).thenReturn(new Page<>(List.of(), 0, 20, 0));
        when(workflow.inbox(anyString(), any(), any())).thenReturn(new Page<>(List.of(), 0, 20, 0));
        mvc.perform(get("/api/core/v1/workspaces/lab-a/metadata")).andExpect(status().isOk());
        mvc.perform(get("/api/core/v1/workspaces/lab-a/tasks")).andExpect(status().isOk());
        grant();
        mvc.perform(get("/api/core/v1/workspaces/lab-a/metadata")).andExpect(status().isForbidden());
        mvc.perform(get("/api/core/v1/workspaces/lab-a/tasks")).andExpect(status().isForbidden());
        verify(metadata, times(1)).search(anyString(), any(), any());
        verify(workflow, times(1)).inbox(anyString(), any(), any());
    }

    @Test void otherWorkspaceAndServiceAccountHaveNoImplicitGrant() throws Exception {
        grant("document:view");
        mvc.perform(get("/api/core/v1/workspaces/lab-b/metadata")).andExpect(status().isForbidden());
        when(user.isMember("lab-a")).thenReturn(false);
        when(user.memberships()).thenReturn(Set.of());
        mvc.perform(get("/api/core/v1/workspaces/lab-a/metadata")).andExpect(status().isForbidden());
        verifyNoInteractions(directory, metadata);
    }

    @Test void outageFailsClosedAndNoWriteOrProcessStartBypassExists() throws Exception {
        when(directory.rolePermissions("lab-a")).thenThrow(CoreException.unavailable("down"));
        mvc.perform(get("/api/core/v1/workspaces/lab-a/metadata")).andExpect(status().isServiceUnavailable());
        grant();
        mvc.perform(post("/api/core/v1/workspaces/lab-a/metadata").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/core/v1/workspaces/lab-a/files").file("file", new byte[]{1}))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/core/v1/workspaces/lab-a/processes/example/instances")
            .contentType("application/json").content("{\"businessKey\":\"example\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(metadata, files, workflow);
    }
}
