package vn.haui.dx.apps.servicedesk.intelligence.context;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.apps.servicedesk.clarification.ClarificationEvent;
import vn.haui.dx.apps.servicedesk.clarification.TicketContextStore;
import vn.haui.dx.hpdi.common.api.CapabilityException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ContextSnapshotAssembler {
    private final TicketService tickets;
    private final TicketContextStore store;
    private final ObjectMapper json;
    private final ContextPolicyCatalog policy;

    public ContextSnapshotAssembler(
            TicketService tickets, TicketContextStore store, ObjectMapper json, ContextPolicyCatalog policy) {
        this.tickets = tickets;
        this.store = store;
        this.json = json.copy().enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.policy = policy;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ContextSnapshot read(String workspace, UUID id) {
        tickets.getForIntelligence(workspace, id);
        var state = store.state(workspace, id, false)
                .orElseThrow(() -> CapabilityException.notFound("Ticket not found"));
        return assemble(workspace, state, store.events(workspace, id));
    }

    public ContextSnapshot assemble(
            String workspace, TicketContextStore.TicketState state, List<ClarificationEvent> events) {
        if (events.size() > policy.maxEvents()) {
            throw CapabilityException.conflict("Clarification snapshot limit reached");
        }
        var sources = new ArrayList<ContextSnapshot.Source>();
        sources.add(new ContextSnapshot.Source("title", 0, state.title(), null, false));
        sources.add(new ContextSnapshot.Source("description", 0, state.description(), null, false));
        long revision = 0;
        for (var event : events) {
            if (!state.id().equals(event.ticketId()) || event.expectedRevision() != revision
                    || event.contextRevision() != revision + 1 || event.answers().size() > 8) {
                throw CapabilityException.conflict("Incomplete clarification snapshot");
            }
            revision = event.contextRevision();
            for (int index = 0; index < event.answers().size(); index++) {
                var answer = event.answers().get(index);
                sources.add(new ContextSnapshot.Source(
                        "clarification:" + event.id() + ":" + index, revision,
                        answer.value(), answer.slot(), answer.correction()));
            }
        }
        if (revision != state.revision()) {
            throw CapabilityException.conflict("Context revision changed");
        }
        try {
            String digest = ContextPolicyCatalog.digest(json.writeValueAsBytes(Map.of(
                    "workspace", workspace, "ticketId", state.id(), "revision", revision,
                    "sources", sources, "policyBundleDigest", policy.bundleDigest())));
            return new ContextSnapshot(workspace, state.id(), revision, sources, digest);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("Cannot fingerprint context snapshot", failure);
        }
    }
}
