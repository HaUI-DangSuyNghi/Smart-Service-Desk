package vn.haui.dx.hpdi.human;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.human.internal.WorkspaceActivityJournal;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class WorkspaceActivityJournalTest {
    @Test void journalRejectsContentAndCredentialDetailsBeforeWritingAudit() {
        var human=mock(HumanContext.class); var audit=mock(AuditService.class);
        var journal=new WorkspaceActivityJournal(human,audit);
        for (String key:java.util.List.of("description","accessTokenId","passwordKey"))
            assertThrows(CapabilityException.class,()->journal.record("lab-a",
                new BusinessActivity("example.request.create","example.request","request-1",Map.of(key,"sensitive"))));
        verifyNoInteractions(audit);
        journal.record("lab-a",new BusinessActivity("example.request.create","example.request","request-1",Map.of("processInstanceId","process-1")));
        verify(human,times(4)).currentActor("lab-a");
        verify(audit).record("example.request.create","example.request","request-1","lab-a",Map.of("processInstanceId","process-1"));
    }
}
