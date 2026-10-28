package vn.haui.dx.apps.servicedesk;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.haui.dx.hpdi.process.api.ProcessSnapshot;

import static org.junit.jupiter.api.Assertions.*;

class ServiceDeskProcessStateTest {
    private static final UUID TICKET = UUID.randomUUID();

    @ParameterizedTest
    @CsvSource({
            "service-desk,handle,true",
            "service-desk,confirm,true",
            "service-desk,review,false",
            "service-desk-reviewed,review,true",
            "service-desk-reviewed,handle,true",
            "service-desk-reviewed,confirm,true",
            "service-desk-reviewed,intake,false",
            "room-request,review,false"
    })
    void acceptsOnlyRegisteredOpenStages(String key, String task, boolean expected) {
        assertEquals(expected, open(snapshot("process", "lab-a", "owner", key, TICKET.toString(),
                true, null, null, List.of(new ProcessSnapshot.TaskState(task, true)))));
    }

    @Test
    void rejectsMissingHistoryForeignCorrelationAndInactiveProcesses() {
        assertFalse(open(null));
        var tasks = List.of(new ProcessSnapshot.TaskState("review", true));
        for (var state : List.of(
                snapshot("other-process", "lab-a", "owner", "service-desk-reviewed", TICKET.toString(), true, null, null, tasks),
                snapshot("process", "lab-b", "owner", "service-desk-reviewed", TICKET.toString(), true, null, null, tasks),
                snapshot("process", "lab-a", "other-owner", "service-desk-reviewed", TICKET.toString(), true, null, null, tasks),
                snapshot("process", "lab-a", "owner", "service-desk-reviewed", "other-ticket", true, null, null, tasks),
                snapshot("process", "lab-a", "owner", "service-desk-reviewed", TICKET.toString(), false, null, null, tasks),
                snapshot("process", "lab-a", "owner", "service-desk-reviewed", TICKET.toString(), true, Instant.now(), null, tasks),
                snapshot("process", "lab-a", "owner", "service-desk-reviewed", TICKET.toString(), true, null, "cancelled", tasks),
                snapshot("process", "lab-a", "owner", null, TICKET.toString(), true, null, null, tasks),
                snapshot("process", "lab-a", "owner", "service-desk-reviewed", TICKET.toString(), true, null, null, List.of()),
                snapshot("process", "lab-a", "owner", "service-desk-reviewed", TICKET.toString(), true, null, null,
                        List.of(new ProcessSnapshot.TaskState("review", false))),
                snapshot("process", "lab-a", "owner", "service-desk-reviewed", TICKET.toString(), true, null, null,
                        List.of(new ProcessSnapshot.TaskState("review", true), new ProcessSnapshot.TaskState("handle", true))))) {
            assertFalse(open(state), state.toString());
        }
    }

    private static boolean open(ProcessSnapshot state) {
        return ServiceDeskProcessState.isOpen("lab-a", TICKET, "owner", "process", state);
    }

    private static ProcessSnapshot snapshot(
            String id, String workspace, String owner, String key, String businessKey,
            boolean history, Instant endedAt, String deleteReason, List<ProcessSnapshot.TaskState> tasks) {
        return new ProcessSnapshot(id, workspace, owner, key, businessKey, history, endedAt, deleteReason, null, tasks);
    }
}
