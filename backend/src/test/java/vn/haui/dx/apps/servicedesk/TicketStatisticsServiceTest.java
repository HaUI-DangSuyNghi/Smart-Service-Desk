package vn.haui.dx.apps.servicedesk;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import vn.haui.dx.hpdi.process.api.ProcessObservations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class TicketStatisticsServiceTest {
    @Test void countsEveryTicketAcrossBatchesAndLimitsPlatformObservationRequests() {
        var repository=mock(TicketRepository.class);
        var observations=mock(ProcessObservations.class);
        var links=IntStream.rangeClosed(1,1001).mapToObj(i->new TicketRepository.ProcessLink(new UUID(0,i),"owner","p-"+i)).toList();
        when(repository.statisticsBatch(eq("lab-a"),isNull(),isNull(),isNull(),isNull(),nullable(UUID.class),eq(1000)))
            .thenReturn(links.subList(0,1000),links.subList(1000,1001),List.of());
        when(observations.snapshots(eq("lab-a"),anySet())).thenAnswer(call->{
            Set<String> ids=call.getArgument(1); assertTrue(ids.size()<=1000); return List.of();
        });
        var result=new TicketStatisticsService(repository,observations).statistics("lab-a",null,null,null,null);
        assertEquals(1001,result.total()); assertEquals(1001,result.unclassified());
        assertEquals(0,result.awaitingHandling()+result.awaitingConfirmation()+result.completed());
    }
}
