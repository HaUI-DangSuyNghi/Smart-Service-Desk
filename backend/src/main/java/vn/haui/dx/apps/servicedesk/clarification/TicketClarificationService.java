package vn.haui.dx.apps.servicedesk.clarification;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextPolicyCatalog;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.ActivityJournal;
import vn.haui.dx.hpdi.human.api.BusinessActivity;
import vn.haui.dx.hpdi.human.api.HumanContext;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

@Service
public class TicketClarificationService {
    public record Revision(String contractVersion, UUID ticketId, long contextRevision) {}

    public record Result(
            String contractVersion, UUID ticketId, UUID answerId, long contextRevision,
            String assessmentStatus, boolean replayed) {}

    private final TicketService tickets;
    private final TicketContextStore store;
    private final HumanContext human;
    private final ActivityJournal audit;
    private final ContextPolicyCatalog policy;
    private final ObjectMapper json;
    private final org.springframework.context.ApplicationEventPublisher events;

    public TicketClarificationService(
            TicketService tickets, TicketContextStore store, HumanContext human,
            ActivityJournal audit, ContextPolicyCatalog policy, ObjectMapper json,
            org.springframework.context.ApplicationEventPublisher events) {
        this.tickets = tickets;
        this.store = store;
        this.human = human;
        this.audit = audit;
        this.policy = policy;
        this.json = json.copy().enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.events = events;
    }

    @Transactional
    public Result append(String workspace, UUID id, ClarificationRequest request) {
        tickets.get(workspace, id);
        var actor = human.currentActor(workspace);
        validate(request);

        // Khóa aggregate trước kiểm tra replay/CAS để hai request đồng thời không ghi lặp.
        var state = store.state(workspace, id, true)
                .orElseThrow(() -> CapabilityException.notFound("Ticket not found"));
        String answers = encode(request.answers());
        String digest = ContextPolicyCatalog.digest(encode(Map.of(
                "expectedRevision", request.expectedContextRevision(), "answers", request.answers())));
        var replay = store.replay(workspace, id, actor.subject(), request.clientAnswerId());
        if (replay.isPresent()) {
            if (!digest.equals(replay.get().payloadDigest())) {
                throw CapabilityException.conflict("Answer id already used with different payload");
            }
            return result(replay.get(), true);
        }

        if (state.revision() != request.expectedContextRevision()) {
            throw CapabilityException.conflict("Context revision changed");
        }
        if (state.revision() >= policy.maxEvents()) {
            throw CapabilityException.conflict("Clarification limit reached; manual review required");
        }

        var event = store.append(workspace, id, actor.subject(), request, answers, digest);
        audit.record(workspace, new BusinessActivity(
                "servicedesk.ticket.clarify", "servicedesk.ticket", id.toString(),
                Map.of("answerId", event.id().toString(), "previousContextVersion", Long.toString(state.revision()),
                        "contextVersion", Long.toString(event.contextRevision()))));
        events.publishEvent(new vn.haui.dx.apps.servicedesk.TicketContextChanged(
                workspace, id, event.contextRevision()));
        return result(event, false);
    }

    @Transactional(readOnly = true)
    public Revision revision(String workspace, UUID id) {
        tickets.get(workspace, id);
        var state = store.state(workspace, id, false)
                .orElseThrow(() -> CapabilityException.notFound("Ticket not found"));
        return new Revision("ticket-context-revision@0.1.0", id, state.revision());
    }

    private void validate(ClarificationRequest input) {
        if (input == null || !ClarificationRequest.VERSION.equals(input.contractVersion())
                || input.clientAnswerId() == null || input.expectedContextRevision() == null
                || input.expectedContextRevision() < 0 || input.answers() == null
                || input.answers().isEmpty() || input.answers().size() > 8) {
            throw CapabilityException.validation("Invalid clarification");
        }
        var slots = new HashSet<String>();
        for (var answer : input.answers()) {
            if (answer == null || !policy.answerSlots().contains(answer.slot())
                    || !slots.add(answer.slot()) || answer.value() == null
                    || answer.value().isBlank() || answer.value().length() > 256
                    || answer.value().codePoints().anyMatch(Character::isISOControl)) {
                throw CapabilityException.validation("Invalid clarification answer");
            }
        }
    }

    private static Result result(ClarificationEvent event, boolean replay) {
        return new Result(ClarificationRequest.VERSION, event.ticketId(), event.id(),
                event.contextRevision(), "AI07_REFRESH_REQUIRED", replay);
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw CapabilityException.validation("Invalid clarification encoding");
        }
    }
}
