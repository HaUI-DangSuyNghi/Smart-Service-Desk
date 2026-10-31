package vn.haui.dx.apps.servicedesk;
import java.util.*;
import java.util.stream.IntStream;
import java.time.*;
import org.junit.jupiter.api.Test;
import vn.haui.dx.hpdi.process.api.ProcessObservations;
import vn.haui.dx.hpdi.process.api.ProcessSnapshot;
import vn.haui.dx.hpdi.process.api.SlaPolicy;
import vn.haui.dx.hpdi.process.api.BusinessCalendar;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class TicketStatisticsServiceTest {
    @Test void classifiesAllBusinessStatesWithoutCountingReworkGenerations() {
        var id = UUID.randomUUID();
        var link = new TicketRepository.ProcessLink(id, "owner", "process");
        for (var entry : Map.of("intake", "INTAKE", "review", "AWAITING_REVIEW", "handle", "ASSIGNED", "confirm", "AWAITING_CONFIRMATION").entrySet()) {
            assertEquals(entry.getValue(), TicketStatisticsService.classify("lab-a", link,
                snapshot(id, entry.getKey(), "NOT_STARTED", Map.of())).name());
        }
        for (String state : List.of("IN_PROGRESS", "PENDING")) {
            assertEquals(state, TicketStatisticsService.classify("lab-a", link, snapshot(id, "handle", state, Map.of("confirm", "rework"))).name());
        }
        for (String task : List.of("review", "handle")) {
            assertEquals("REOPENED", TicketStatisticsService.classify("lab-a", link, snapshot(id, task, "NOT_STARTED", Map.of("confirm", "rework"))).name());
            assertEquals("REOPENED", TicketStatisticsService.classify("lab-a", new TicketRepository.ProcessLink(id, "owner", "process", true, null),
                snapshot(id, task, "NOT_STARTED", Map.of())).name());
        }
        var follower = new TicketRepository.ProcessLink(id, "owner", "process", false, "ACTIVE");
        assertEquals("FOLLOWING", TicketStatisticsService.classify("lab-a", follower, snapshot(id, "follower", "NOT_STARTED", Map.of())).name());
        var ended = new ProcessSnapshot("process", "lab-a", "owner", ServiceDeskProcess.INTAKE_KEY, id.toString(), true,
            Instant.EPOCH, null, "end", List.of());
        assertEquals("UNCLASSIFIED", TicketStatisticsService.classify("lab-a", follower, ended).name());
        assertEquals("CLOSED", TicketStatisticsService.classify("lab-a", new TicketRepository.ProcessLink(id, "owner", "process", false, "CLOSED"), ended).name());
        assertEquals("UNCLASSIFIED", TicketStatisticsService.classify("lab-a", link, null).name());
        assertEquals("UNCLASSIFIED", TicketStatisticsService.classify("foreign", link, ended).name());
        for (String action:List.of("CLOSED","CANCELLED")) {
            var stopped=new ProcessSnapshot("process","lab-a","owner",ServiceDeskProcess.INTAKE_KEY,id.toString(),true,
                Instant.EPOCH,"REQUEST_"+action+": Owner reason",null,List.of());
            assertEquals(action,TicketStatisticsService.classify("lab-a",link,stopped).name());
        }
        assertEquals("UNCLASSIFIED", TicketStatisticsService.classify("lab-a", link, snapshot(id, "unexpected", "NOT_STARTED", Map.of())).name());
        assertEquals("UNCLASSIFIED", TicketStatisticsService.classify("lab-a", link, snapshot(id, "handle", "CORRUPT", Map.of())).name());
    }

    private ProcessSnapshot snapshot(UUID id, String task, String state, Map<String, String> outcomes) {
        return new ProcessSnapshot("process", "lab-a", "owner", ServiceDeskProcess.INTAKE_KEY, id.toString(), true, null, null, null,
            List.of(new ProcessSnapshot.TaskState(task, true, "task", state)), outcomes);
    }

    @Test void currentOverdueUsesUnfinishedMilestonesPolicyAndPauseInsteadOfHistoricalBreaches() {
        var calendar = new BusinessCalendar("Asia/Ho_Chi_Minh", Set.of(DayOfWeek.values()),
            List.of(new BusinessCalendar.Shift(LocalTime.MIN, LocalTime.MAX)), Set.of());
        var targets = new EnumMap<SlaPolicy.Priority, SlaPolicy.Target>(SlaPolicy.Priority.class);
        for (var priority : SlaPolicy.Priority.values()) targets.put(priority, new SlaPolicy.Target(30, 60, true));
        var policy = new SlaPolicy("test", calendar, targets, 0.5, SlaPolicy.Priority.CRITICAL, false);
        var at = Instant.parse("2026-01-01T00:00:00Z");
        var state = new TicketSlaService.State(UUID.randomUUID(), "process", "task", policy, SlaPolicy.Priority.LOW, SlaPolicy.Priority.LOW,
            at, at.plusSeconds(1800), at.plusSeconds(3600), null, null, null, true, true, true);
        assertFalse(TicketStatisticsService.currentlyOverdue(state, at.plusSeconds(1800)));
        assertTrue(TicketStatisticsService.currentlyOverdue(state, at.plusSeconds(1801)));
        var started = new TicketSlaService.State(state.ticketId(), "process", "task", policy, state.base(), state.priority(),
            at, state.startBy(), state.resolveBy(), at.plusSeconds(1801), null, null, true, true, true);
        assertFalse(TicketStatisticsService.currentlyOverdue(started, at.plusSeconds(3500)));
        assertTrue(TicketStatisticsService.currentlyOverdue(started, at.plusSeconds(3601)));
        var paused = new TicketSlaService.State(state.ticketId(), "process", "task", policy, state.base(), state.priority(),
            at, state.startBy(), state.resolveBy(), at.plusSeconds(1801), null, at.plusSeconds(2000));
        assertFalse(TicketStatisticsService.currentlyOverdue(paused, at.plusSeconds(7200)));
        var resolved = new TicketSlaService.State(state.ticketId(), "process", "task", policy, state.base(), state.priority(),
            at, state.startBy(), state.resolveBy(), at.plusSeconds(1801), at.plusSeconds(4000), null, false, true, true);
        assertFalse(TicketStatisticsService.currentlyOverdue(resolved, at.plusSeconds(8000)));
    }

    @Test void countsEveryTicketAcrossBatchesAndLimitsPlatformObservationRequests() {
        var repository=mock(TicketRepository.class);
        var observations=mock(ProcessObservations.class);
        var links=IntStream.rangeClosed(1,1001).mapToObj(i->new TicketRepository.ProcessLink(new UUID(0,i),"owner","p-"+i)).toList();
        when(repository.statisticsBatch(eq("lab-a"),isNull(),isNull(),isNull(),isNull(),nullable(UUID.class),eq(1000)))
            .thenReturn(links.subList(0,1000),links.subList(1000,1001),List.of());
        when(observations.snapshots(eq("lab-a"),anySet())).thenAnswer(call->{
            Set<String> ids=call.getArgument(1); assertTrue(ids.size()<=1000); return List.of();
        });
        var result=new TicketStatisticsService(repository,observations,mock(TicketSlaService.class)).statistics("lab-a",null,null,null,null);
        assertEquals(1001,result.total()); assertEquals(1001,result.unclassified());
        assertEquals(0,result.awaitingHandling()+result.awaitingConfirmation()+result.completed());
    }
}
