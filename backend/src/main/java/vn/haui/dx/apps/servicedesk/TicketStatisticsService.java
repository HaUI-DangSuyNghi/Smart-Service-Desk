package vn.haui.dx.apps.servicedesk;
import java.util.*;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.hpdi.process.api.ProcessObservations;
import vn.haui.dx.hpdi.process.api.ProcessSnapshot;

@Service
class TicketStatisticsService {
    private final TicketRepository tickets;
    private final ProcessObservations processes;
    private final TicketSlaService sla;
    TicketStatisticsService(TicketRepository tickets, ProcessObservations processes, TicketSlaService sla) {
        this.tickets=tickets; this.processes=processes; this.sla=sla;
    }
    enum Status { INTAKE, FOLLOWING, AWAITING_REVIEW, ASSIGNED, IN_PROGRESS, PENDING, REOPENED, AWAITING_CONFIRMATION, CLOSED, CANCELLED, UNCLASSIFIED }

    static Status classify(String workspace, TicketRepository.ProcessLink ticket, ProcessSnapshot snapshot) {
        if (snapshot == null || !snapshot.historyPresent() || !workspace.equals(snapshot.workspace())
                || !ticket.creator().equals(snapshot.owner()) || !ticket.id().toString().equals(snapshot.businessKey())
                || !Set.of(ServiceDeskProcess.KEY, ServiceDeskProcess.REVIEWED_KEY, ServiceDeskProcess.INTAKE_KEY).contains(snapshot.processKey()))
            return Status.UNCLASSIFIED;
        if (snapshot.endedAt() != null && snapshot.deleteReason() != null && snapshot.deleteReason().startsWith("REQUEST_CANCELLED:")) return Status.CANCELLED;
        if (snapshot.endedAt() != null && snapshot.deleteReason() != null && snapshot.deleteReason().startsWith("REQUEST_CLOSED:")) return Status.CLOSED;
        if (snapshot.endedAt() != null)
            return snapshot.deleteReason() == null && "end".equals(snapshot.endActivityId()) && snapshot.tasks().isEmpty()
                && !"ACTIVE".equals(ticket.canonicalState()) ? Status.CLOSED : Status.UNCLASSIFIED;
        if (snapshot.tasks().size() != 1 || !snapshot.tasks().get(0).active()) return Status.UNCLASSIFIED;
        var task = snapshot.tasks().get(0);
        boolean reworked = ticket.reworked() || "rework".equals(snapshot.outcomes().get("confirm"));
        return switch (task.key()) {
            case "intake" -> ServiceDeskProcess.INTAKE_KEY.equals(snapshot.processKey()) ? Status.INTAKE : Status.UNCLASSIFIED;
            case "follower" -> ServiceDeskProcess.INTAKE_KEY.equals(snapshot.processKey()) && "ACTIVE".equals(ticket.canonicalState())
                ? Status.FOLLOWING : Status.UNCLASSIFIED;
            case "review" -> ServiceDeskProcess.reviewed(snapshot.processKey())
                ? reworked ? Status.REOPENED : Status.AWAITING_REVIEW : Status.UNCLASSIFIED;
            case "confirm" -> Status.AWAITING_CONFIRMATION;
            case "handle" -> switch (task.workState()) {
                case "IN_PROGRESS" -> Status.IN_PROGRESS;
                case "PENDING" -> Status.PENDING;
                case "NOT_STARTED" -> reworked ? Status.REOPENED : Status.ASSIGNED;
                default -> Status.UNCLASSIFIED;
            };
            default -> Status.UNCLASSIFIED;
        };
    }

    static boolean currentlyOverdue(TicketSlaService.State state, Instant now) {
        if (state == null || state.resolvedAt() != null) return false;
        var current = state.policy().project(state.base(), state.startBy(), state.resolveBy(), state.firstStarted(),
            state.resolvedAt(), state.pausedAt(), now);
        return state.firstStarted() == null && current.startBreached() || current.resolveBreached();
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public TicketStatistics statistics(String workspace, String creator, String query, Instant from, Instant to) {
        long total=0, handling=0, confirming=0, completed=0, unclassified=0;
        var counts = new EnumMap<Status, Long>(Status.class);
        long overdue = 0;
        Instant now = sla.now();
        UUID after=null;
        while (true) {
            var batch = tickets.statisticsBatch(workspace, creator, query, from, to, after, 1000);
            if (batch.isEmpty()) break;
            total+=batch.size();
            var states = processes.snapshots(workspace, batch.stream().map(TicketRepository.ProcessLink::processId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProcessSnapshot::id, s -> s));
            var slas = sla.batch(workspace, states.values().stream().flatMap(state -> state.tasks().stream())
                .filter(task -> "handle".equals(task.key()) && task.id() != null).map(ProcessSnapshot.TaskState::id).toList());
            for (var ticket : batch) {
                var s = states.get(ticket.processId());
                var status = classify(workspace, ticket, s);
                counts.merge(status, 1L, Long::sum);
                if (status == Status.CLOSED) completed++;
                else if (status == Status.AWAITING_CONFIRMATION) confirming++;
                else if (status == Status.UNCLASSIFIED) unclassified++;
                else if (status != Status.INTAKE && status != Status.FOLLOWING && status != Status.CANCELLED) handling++;
                if (Set.of(Status.ASSIGNED, Status.REOPENED, Status.IN_PROGRESS, Status.PENDING).contains(status)
                        && "handle".equals(s.tasks().get(0).key())) {
                    var state = slas.get(s.tasks().get(0).id());
                    if (state != null && ticket.id().equals(state.ticketId()) && ticket.processId().equals(state.processId())
                            && currentlyOverdue(state, now)) overdue++;
                }
            }
            after=batch.get(batch.size()-1).id();
        }
        return new TicketStatistics(workspace, creator==null ? "workspace" : "own", total, handling, confirming, completed, unclassified,
            counts.getOrDefault(Status.AWAITING_REVIEW, 0L), counts.getOrDefault(Status.ASSIGNED, 0L),
            counts.getOrDefault(Status.IN_PROGRESS, 0L), counts.getOrDefault(Status.PENDING, 0L),
            counts.getOrDefault(Status.REOPENED, 0L), completed, overdue,
            counts.getOrDefault(Status.INTAKE, 0L), counts.getOrDefault(Status.FOLLOWING, 0L), counts.getOrDefault(Status.CANCELLED, 0L));
    }
}
