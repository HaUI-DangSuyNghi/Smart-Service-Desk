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
    TicketStatisticsService(TicketRepository tickets, ProcessObservations processes) { this.tickets=tickets; this.processes=processes; }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public TicketStatistics statistics(String workspace, String creator, String query, Instant from, Instant to) {
        long total=0, handling=0, confirming=0, completed=0, unclassified=0;
        UUID after=null;
        while (true) {
            var batch = tickets.statisticsBatch(workspace, creator, query, from, to, after, 1000);
            if (batch.isEmpty()) break;
            total+=batch.size();
            var states = processes.snapshots(workspace, batch.stream().map(TicketRepository.ProcessLink::processId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProcessSnapshot::id, s -> s));
            for (var ticket : batch) {
                var s = states.get(ticket.processId());
                if (s == null || !s.historyPresent() || !workspace.equals(s.workspace()) || !ticket.creator().equals(s.owner())
                        || !ticket.id().toString().equals(s.businessKey()) || !Set.of(ServiceDeskProcess.KEY,ServiceDeskProcess.REVIEWED_KEY,ServiceDeskProcess.INTAKE_KEY).contains(s.processKey())) unclassified++;
                else if (s.endedAt()!=null && s.deleteReason()==null && "end".equals(s.endActivityId()) && s.tasks().isEmpty()) completed++;
                else if (s.endedAt()==null && s.tasks().size()==1 && s.tasks().get(0).active() && Set.of("handle","review").contains(s.tasks().get(0).key())) handling++;
                else if (s.endedAt()==null && s.tasks().size()==1 && s.tasks().get(0).active() && "confirm".equals(s.tasks().get(0).key())) confirming++;
                else unclassified++;
            }
            after=batch.get(batch.size()-1).id();
        }
        return new TicketStatistics(workspace, creator==null ? "workspace" : "own", total, handling, confirming, completed, unclassified);
    }
}
