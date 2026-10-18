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
        var rows = tickets.statisticsRows(workspace, creator, query, from, to);
        long handling=0, confirming=0, completed=0, unclassified=0;
        for (int offset=0; offset<rows.size(); offset+=1000) {
            var batch = rows.subList(offset, Math.min(offset+1000, rows.size()));
            var states = processes.snapshots(workspace, batch.stream().map(t -> t.process().processInstanceId()).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProcessSnapshot::id, s -> s));
            for (var ticket : batch) {
                var s = states.get(ticket.process().processInstanceId());
                if (s == null || !s.historyPresent() || !workspace.equals(s.workspace()) || !ticket.creator().equals(s.owner())
                        || !ticket.id().toString().equals(s.businessKey()) || !ServiceDeskProcess.KEY.equals(s.processKey())) unclassified++;
                else if (s.endedAt()!=null && s.deleteReason()==null && "end".equals(s.endActivityId()) && s.tasks().isEmpty()) completed++;
                else if (s.endedAt()==null && s.tasks().size()==1 && s.tasks().get(0).active() && "handle".equals(s.tasks().get(0).key())) handling++;
                else if (s.endedAt()==null && s.tasks().size()==1 && s.tasks().get(0).active() && "confirm".equals(s.tasks().get(0).key())) confirming++;
                else unclassified++;
            }
        }
        return new TicketStatistics(workspace, creator==null ? "workspace" : "own", rows.size(), handling, confirming, completed, unclassified);
    }
}
