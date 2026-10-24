package vn.haui.dx.hpdi.human;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.haui.dx.core.audit.api.AuditEntry;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.human.internal.AuditActivityFeed;
import vn.haui.dx.hpdi.process.api.ProcessObservations;
import vn.haui.dx.hpdi.process.api.ProcessSnapshot;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditActivityFeedTest {
    private final AuditService audit = mock(AuditService.class);
    private final ProcessObservations processes = mock(ProcessObservations.class);
    private final Instant at = Instant.parse("2026-10-24T08:00:00Z");

    private AuditEntry entry(String action, String type, String resource, Map<String, Object> details) {
        return new AuditEntry(UUID.randomUUID(), at, "me", action, type, resource, "lab-a", "req-1", details);
    }

    @Test void resolvesProcessOfEachEntryAndLooksUpMissingKeysInOneCall() {
        var completeDetails = new HashMap<String, Object>(Map.of("taskKey", "approve", "processInstanceId", "p-2",
            "outcome", "approved"));
        when(audit.mine(eq("lab-a"), any(), any())).thenReturn(new vn.haui.dx.core.common.Page<>(List.of(
            entry("process.start", "process", "p-1", Map.of("processKey", "loan", "businessKey", "b-1")),
            entry("task.complete", "task", "t-9", completeDetails),
            entry("file.upload", "file", "f-1", Map.of())), 0, 20, 3));
        when(processes.snapshots("lab-a", Set.of("p-1", "p-2"))).thenReturn(List.of(
            new ProcessSnapshot("p-2", "lab-a", "someone", "review", "b-2", true, null, null, null, List.of())));

        var items = new AuditActivityFeed(audit, processes).mine("lab-a", new PageRequest(0, 20)).items();

        // process.start: id ở resource, key có sẵn trong details
        assertEquals("p-1", items.get(0).processInstanceId());
        assertEquals("loan", items.get(0).processKey());
        // task.complete: id trong details, key tra qua ProcessObservations
        assertEquals("p-2", items.get(1).processInstanceId());
        assertEquals("review", items.get(1).processKey());
        assertEquals("approved", items.get(1).details().get("outcome"));
        // Không thuộc quy trình
        assertNull(items.get(2).processInstanceId());
        assertNull(items.get(2).processKey());
        verify(processes, times(1)).snapshots(anyString(), anySet());
    }

    @Test void skipsProcessLookupWhenPageHasNoProcessEntries() {
        when(audit.mine(eq("lab-a"), any(), any())).thenReturn(new vn.haui.dx.core.common.Page<>(List.of(
            entry("metadata.create", "metadata", "m-1", Map.of())), 0, 20, 1));
        new AuditActivityFeed(audit, processes).mine("lab-a", new PageRequest(0, 20));
        verifyNoInteractions(processes);
    }
}
