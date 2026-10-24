package vn.haui.dx.hpdi.process;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.Actor;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.process.api.*;
// Import rõ ràng: java.lang cũng có ProcessHandle, import * không đủ để phân biệt
import vn.haui.dx.hpdi.process.api.ProcessHandle;
import vn.haui.dx.hpdi.process.internal.ContributedProcessCatalog;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContributedProcessCatalogTest {
    private final HumanContext human = mock(HumanContext.class);
    private final ProcessCapability processes = mock(ProcessCapability.class);

    private final ProcessDefinition selfService = new ProcessDefinition("loan", Set.of("requester"), Set.of("title", "note"),
        Map.of("approve", Set.of("rejected", "approved"), "handover", Set.of("delivered")))
        .allowSelfService()
        .withLabels(ProcessLabels.named("Mượn đồ")
            .task("approve", "Duyệt")
            .outcome("approved", "Đồng ý")
            .outcome("rejected", "Từ chối")
            .variable("title", "Tên đồ")
            .longVariable("note", "Ghi chú"));
    private final ProcessDefinition appOwned = new ProcessDefinition("ticket-like", Set.of("requester"), Set.of(),
        Map.of("handle", Set.of("resolved")));

    private ContributedProcessCatalog catalog(String... roles) {
        when(human.currentActor("lab-a")).thenReturn(new Actor("me", "lab-a", Set.of(roles), Set.of()));
        ProcessContribution contribution = () -> List.of(selfService, appOwned);
        return new ContributedProcessCatalog(List.of(contribution), human, processes);
    }

    @Test void describesLabelsInDeclaredOrderAndFallsBackToKeys() {
        var loan = catalog("requester").describe("lab-a").stream()
            .filter(process -> process.processKey().equals("loan")).findFirst().orElseThrow();
        assertEquals("Mượn đồ", loan.name());
        assertEquals(List.of("approve", "handover"), loan.tasks().stream().map(ProcessDescriptor.Task::key).toList());
        assertEquals("Duyệt", loan.tasks().get(0).name());
        // Thứ tự nút theo khai báo nhãn, không theo Set
        assertEquals(List.of("Đồng ý", "Từ chối"),
            loan.tasks().get(0).outcomes().stream().map(ProcessDescriptor.Option::label).toList());
        assertEquals("handover", loan.tasks().get(1).name());
        assertEquals("delivered", loan.tasks().get(1).outcomes().get(0).label());
        assertEquals(List.of(new ProcessDescriptor.Field("title", "Tên đồ", false),
            new ProcessDescriptor.Field("note", "Ghi chú", true)), loan.startFields());
    }

    @Test void canStartNeedsSelfServiceAndStarterRole() {
        var asRequester = catalog("requester").describe("lab-a");
        assertTrue(asRequester.stream().anyMatch(process -> process.processKey().equals("loan") && process.canStart()));
        assertTrue(asRequester.stream().anyMatch(process -> process.processKey().equals("ticket-like") && !process.canStart()));
        assertTrue(catalog("agent").describe("lab-a").stream().noneMatch(ProcessDescriptor::canStart));
    }

    @Test void selfServiceStartGeneratesBusinessKeyAndRejectsAppOwnedProcesses() {
        var catalog = catalog("requester");
        var handle = new ProcessHandle("instance", "loan", "generated", "lab-a");
        when(processes.start(eq("lab-a"), eq("loan"), anyString(), eq(Map.<String, Object>of("title", "Máy chiếu")))).thenReturn(handle);
        assertSame(handle, catalog.startSelfService("lab-a", "loan", Map.of("title", "Máy chiếu")));
        verify(processes).start(eq("lab-a"), eq("loan"), argThat(key -> !key.isBlank()), anyMap());

        var forbidden = assertThrows(CapabilityException.class,
            () -> catalog.startSelfService("lab-a", "ticket-like", Map.of()));
        assertEquals(CapabilityException.Code.FORBIDDEN, forbidden.code());
        var missing = assertThrows(CapabilityException.class, () -> catalog.startSelfService("lab-a", "unknown", Map.of()));
        assertEquals(CapabilityException.Code.NOT_FOUND, missing.code());
    }

    @Test void singleLineFieldsKeep200CharactersMultilineGoLonger() {
        var catalog = catalog("requester");
        var tooLong = assertThrows(CapabilityException.class,
            () -> catalog.startSelfService("lab-a", "loan", Map.of("title", "x".repeat(201))));
        assertEquals(CapabilityException.Code.VALIDATION_FAILED, tooLong.code());
        catalog.startSelfService("lab-a", "loan", Map.of("title", "x", "note", "x".repeat(2000)));
        verify(processes).start(eq("lab-a"), eq("loan"), anyString(),
            eq(Map.<String, Object>of("title", "x", "note", "x".repeat(2000))));
    }

    @Test void labelsForUnknownKeysFailAtStartup() {
        var definition = new ProcessDefinition("loan", Set.of("requester"), Set.of(), Map.of("approve", Set.of("approved")));
        assertThrows(IllegalArgumentException.class, () -> definition.withLabels(ProcessLabels.named("x").task("typo", "y")));
        assertThrows(IllegalArgumentException.class, () -> definition.withLabels(ProcessLabels.named("x").outcome("typo", "y")));
        assertThrows(IllegalArgumentException.class, () -> definition.withLabels(ProcessLabels.named("x").variable("title", "y")));
    }
}
