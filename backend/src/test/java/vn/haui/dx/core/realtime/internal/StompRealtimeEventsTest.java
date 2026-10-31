package vn.haui.dx.core.realtime.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.haui.dx.core.realtime.api.RealtimeEvent;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StompRealtimeEventsTest {
    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<SimpMessagingTemplate> provider = mock(ObjectProvider.class);
    private final StompRealtimeEvents events;

    StompRealtimeEventsTest() {
        when(provider.getObject()).thenReturn(template);
        events = new StompRealtimeEvents(provider);
    }

    @AfterEach void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    private void complete(boolean committed) {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        if (committed) synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(item -> item.afterCompletion(committed
            ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test void outsideTransactionSendsImmediately() {
        events.toTopic("/topic/workspaces/a/work", RealtimeEvent.of("WORK_CHANGED", "a", "work", null));
        verify(template).convertAndSend(eq("/topic/workspaces/a/work"), any(RealtimeEvent.class));
    }

    @Test void insideTransactionSendsOnceAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        events.toTopic("/topic/workspaces/a/tickets", RealtimeEvent.of("TICKET_CHANGED", "a", "ticket", "1"));
        events.toTopic("/topic/workspaces/a/tickets", RealtimeEvent.of("TICKET_CHANGED", "a", "ticket", "1"));
        events.toUser("u1", RealtimeEvent.of("TICKET_CHANGED", "a", "ticket", "1"));
        verifyNoInteractions(template);
        complete(true);
        verify(template, times(1)).convertAndSend(eq("/topic/workspaces/a/tickets"), any(RealtimeEvent.class));
        verify(template, times(1)).convertAndSendToUser(eq("u1"), eq("/queue/events"), any(RealtimeEvent.class));
    }

    @Test void rollbackSendsNothing() {
        TransactionSynchronizationManager.initSynchronization();
        events.toUser("u1", RealtimeEvent.of("NOTIFICATION", "a", "notification", "n1"));
        complete(false);
        verifyNoInteractions(template);
    }

    @Test void deliveryFailureNeverBreaksCaller() {
        doThrow(new IllegalStateException("broker down")).when(template).convertAndSend(any(String.class), any(Object.class));
        events.toTopic("/topic/workspaces/a/work", RealtimeEvent.of("WORK_CHANGED", "a", "work", null));
    }

    @Test void rejectsNonTopicDestinations() {
        events.toTopic("/queue/other", RealtimeEvent.of("WORK_CHANGED", "a", "work", null));
        verifyNoInteractions(template);
    }
}
