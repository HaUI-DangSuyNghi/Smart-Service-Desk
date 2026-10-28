package vn.haui.dx.apps.servicedesk;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.haui.dx.hpdi.human.api.WorkAlerts;
import vn.haui.dx.hpdi.process.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketAlertRulesTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T09:00:00Z"), ZoneOffset.UTC);
    private final Instant now = clock.instant();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TicketSlaService sla = mock(TicketSlaService.class);
    private final WorkAlerts alerts = mock(WorkAlerts.class);
    private final WorkScheduling scheduling = mock(WorkScheduling.class);
    private final UUID ticket = UUID.randomUUID();
    private final Map<String, Object> row = new HashMap<>();
    private final TicketAlerts service = new TicketAlerts(jdbc, alerts, sla, scheduling);
    @BeforeEach void setup() {
        row.putAll(Map.of("workspace", "lab-a", "ticket_id", ticket, "process_instance_id", "process", "process_key", ServiceDeskProcess.REVIEWED_KEY,
            "review_task_id", "review", "handle_task_id", "handle", "assignee", "agent"));
        row.put("group_generation", 0); row.put("off_generation", 0);
        when(jdbc.queryForList(anyString(), eq("lab-a"), eq("handle"))).thenReturn(List.of(row));
        when(sla.now()).thenReturn(now);
        when(scheduling.reconcile(anyString(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            assertNull(invocation.<Supplier<WorkScheduling.Schedule>>getArgument(3).get()); return true;
        });
    }
    private void state(Instant startBy, Instant due, Instant started, Instant resolved, Instant paused) {
        var calendar = new BusinessCalendar("UTC", Set.of(DayOfWeek.values()), List.of(new BusinessCalendar.Shift(LocalTime.MIN, LocalTime.of(23, 59))), Set.of());
        var targets = new EnumMap<SlaPolicy.Priority, SlaPolicy.Target>(SlaPolicy.Priority.class);
        for (var priority : SlaPolicy.Priority.values()) targets.put(priority, new SlaPolicy.Target(15, 120, true));
        var policy = new SlaPolicy("test", calendar, targets, .5, SlaPolicy.Priority.HIGH, true);
        when(sla.read("lab-a", ticket)).thenReturn(new TicketSlaService.State(ticket, "process", "handle", policy,
            SlaPolicy.Priority.CRITICAL, SlaPolicy.Priority.CRITICAL, now.minus(Duration.ofDays(5)), startBy, due, started, resolved, paused));
    }
    private Set<WorkAlerts.Type> check(Map<String, Boolean> duty) {
        service.check("lab-a", "process", "handle", duty);
        var events = ArgumentCaptor.forClass(WorkAlerts.Event.class);
        verify(alerts, atLeast(0)).publish(events.capture());
        return events.getAllValues().stream().map(WorkAlerts.Event::type).collect(java.util.stream.Collectors.toSet());
    }
    @Test void deadlineEqualityIsNotBreachAndOneNanosecondLaterIsBreach() {
        state(now, now, null, null, null);
        assertFalse(check(null).contains(WorkAlerts.Type.SLA_BREACHED));
        reset(alerts);
        state(now.minusNanos(1), now.minusNanos(1), null, null, null);
        assertEquals(Set.of(WorkAlerts.Type.START_OVERDUE, WorkAlerts.Type.SLA_BREACHED), check(null));
    }

    @Test void changedGroupDoesNotFalselyReportOnDutyIncumbentAsOffDuty() {
        state(now.plusSeconds(600),now.plusSeconds(3600),now.minusSeconds(60),null,null);
        service.check("lab-a","process","handle",Map.of("network-agent",true),Map.of("agent",true),null);
        verify(alerts,never()).publish(any());
    }

    @Test void staleGroupDutyReadIsDiscardedAfterCorrection() {
        row.put("category","NETWORK");
        service.check("lab-a","process","handle",Map.of(),Map.of(),"HARDWARE");
        verifyNoInteractions(alerts);
        verify(sla,never()).read(anyString(),any());
    }
    @Test void lateStartNeverProducesUnstartedAlertAndResolvedWorkDoesNotAlert() {
        state(now.minusSeconds(600), now.plusSeconds(3000), now.minusSeconds(300), null, null);
        assertTrue(check(null).isEmpty());
        reset(alerts);
        state(now.minusSeconds(600), now.minusSeconds(1), now.minusSeconds(300), now.minusSeconds(30), null);
        assertTrue(check(null).isEmpty());
    }
    @Test void pendingThresholdIsStrictAndUsesPauseGenerationEvenWhenSlaClockPaused() {
        state(now.minusSeconds(1000), now.minusSeconds(100), now.minus(Duration.ofDays(4)), null, now.minus(Duration.ofDays(3)));
        row.put("pending_at", Timestamp.from(now.minus(Duration.ofDays(3))));
        assertTrue(check(null).isEmpty());
        reset(alerts);
        row.put("pending_at", Timestamp.from(now.minus(Duration.ofDays(3)).minusMillis(1)));
        assertEquals(Set.of(WorkAlerts.Type.PENDING_TOO_LONG), check(null));
    }
    @Test void warningUsesExistingPolicyAndDutyRequiresActualGroupRoster() {
        state(now.plusSeconds(300), now.plusSeconds(600), now.minusSeconds(600), null, null);
        assertEquals(Set.of(WorkAlerts.Type.SLA_WARNING, WorkAlerts.Type.NO_ONE_ON_DUTY, WorkAlerts.Type.ASSIGNEE_OFF_DUTY), check(Map.of("agent", false)));
    }
    @Test void missingSlaAndObsoleteReviewAreFailClosed() {
        assertTrue(check(null).isEmpty());
        verifyNoInteractions(alerts);
    }
}
