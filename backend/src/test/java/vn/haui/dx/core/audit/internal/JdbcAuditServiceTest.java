package vn.haui.dx.core.audit.internal;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import vn.haui.dx.core.audit.api.AuditQuery;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.common.PageRequest;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.Membership;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import vn.haui.dx.core.identity.api.WorkspaceRole;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static vn.haui.dx.core.identity.api.WorkspaceRole.*;

class JdbcAuditServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    /** Người dùng giả với membership cố định. */
    private record User(boolean platformAdmin, Set<Membership> memberships) implements CurrentUser {
        public String sub() { return "sub-1"; }
        public String username() { return "user"; }
        public boolean isMember(String workspace) { return memberships.stream().anyMatch(m -> m.workspace().equals(workspace)); }
        public boolean hasRole(String workspace, WorkspaceRole... anyOf) {
            return memberships.stream().anyMatch(m -> m.workspace().equals(workspace) && List.of(anyOf).contains(m.role()));
        }
    }

    private JdbcAuditService service(boolean admin, Membership... memberships) {
        CurrentUser user = new User(admin, Set.of(memberships));
        WorkspaceAccess access = mock(WorkspaceAccess.class);
        when(access.current()).thenReturn(user);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        return new JdbcAuditService(jdbc, access, new ObjectMapper());
    }

    @SuppressWarnings("unchecked")
    private void searched() {
        verify(jdbc).query(contains("order by occurred_at desc"), any(RowMapper.class), any(Object[].class));
    }

    @Test void coordinatorAndPlatformAdminMayRead() {
        service(false, new Membership("lab-a", COORDINATOR)).search("lab-a", new AuditQuery(null, null, null, null), PageRequest.of(null, null));
        searched();
        reset(jdbc);
        service(true).search("lab-a", new AuditQuery(null, null, "task.complete", null), PageRequest.of(0, 10));
        searched();
    }

    @Test void mineIsOpenToMembersButAlwaysFiltersByCallerFromToken() {
        // Agent không đọc được audit chung nhưng đọc được của chính mình; actor do client gửi bị bỏ qua
        service(false, new Membership("lab-a", AGENT))
            .mine("lab-a", new AuditQuery(null, null, null, "someone-else"), PageRequest.of(0, 10));
        verify(jdbc).queryForObject(contains("and actor = ?"), eq(Long.class), eq("lab-a"), eq("sub-1"));
        searched();
    }

    @Test void mineRequiresWorkspaceMembershipBeforeQuerying() {
        WorkspaceAccess access = mock(WorkspaceAccess.class);
        doThrow(CoreException.forbidden("Workspace membership required")).when(access).requireMember("lab-a");
        var denied = new JdbcAuditService(jdbc, access, new ObjectMapper());
        assertThrows(CoreException.class,
            () -> denied.mine("lab-a", new AuditQuery(null, null, null, null), PageRequest.of(null, null)));
        verifyNoInteractions(jdbc);
    }

    @Test void othersAreForbiddenBeforeQuerying() {
        for (JdbcAuditService denied : List.of(service(false, new Membership("lab-a", AGENT)), service(false, new Membership("lab-b", COORDINATOR)))) {
            var error = assertThrows(CoreException.class,
                () -> denied.search("lab-a", new AuditQuery(null, null, null, null), PageRequest.of(null, null)));
            assertEquals(ErrorCode.FORBIDDEN, error.code());
        }
        verifyNoInteractions(jdbc);
    }

    @Test void recordTakesActorFromTokenAndRejectsBadInput() {
        var audit = service(false, new Membership("lab-a", AGENT));
        audit.record("servicedesk.ticket.create", "servicedesk.ticket", "T-1", "lab-a", Map.of("priority", "P2"));
        verify(jdbc).update(contains("insert into audit_event"), any(), eq("sub-1"), eq("servicedesk.ticket.create"),
            eq("servicedesk.ticket"), eq("T-1"), eq("lab-a"), anyString(), eq("{\"priority\":\"P2\"}"));
        for (String action : new String[] {"create", "Metadata.Create", "metadata.", "a b.c", null})
            assertThrows(IllegalArgumentException.class, () -> audit.record(action, "x", "1", "lab-a", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> audit.record("x.y", " ", "1", "lab-a", Map.of()));
    }
}
