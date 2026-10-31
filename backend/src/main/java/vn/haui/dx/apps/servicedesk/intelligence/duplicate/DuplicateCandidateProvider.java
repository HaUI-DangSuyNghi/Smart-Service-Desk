package vn.haui.dx.apps.servicedesk.intelligence.duplicate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import vn.haui.dx.apps.servicedesk.ServiceDeskProcessState;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.apps.servicedesk.clarification.TicketContextStore;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextPolicyCatalog;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextProjection;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextSlotExtractor;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextSnapshot;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextSnapshotAssembler;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.process.api.ProcessObservations;
import vn.haui.dx.hpdi.process.api.ProcessSnapshot;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Đọc có giới hạn trong app; trạng thái mở luôn lấy từ process capability. */
@Service
public class DuplicateCandidateProvider {
    public record Row(
            UUID id, String creator, String processId, Instant createdAt,
            TicketContextStore.TicketState state) {}

    public record Candidate(Row row, ContextSnapshot snapshot, ContextProjection context, ProcessSnapshot workflow) {}

    public record Batch(List<Candidate> candidates, int scanned, boolean windowFull, String digest) {
        public Batch { candidates = List.copyOf(candidates); }
    }

    private final JdbcTemplate jdbc;
    private final TicketService tickets;
    private final TicketContextStore store;
    private final ContextSnapshotAssembler snapshots;
    private final ContextSlotExtractor extractor;
    private final ProcessObservations processes;
    private final HumanContext human;
    private final ContextPolicyCatalog policy;

    public DuplicateCandidateProvider(
            JdbcTemplate jdbc, TicketService tickets, TicketContextStore store,
            ContextSnapshotAssembler snapshots, ContextSlotExtractor extractor,
            ProcessObservations processes, HumanContext human, ContextPolicyCatalog policy) {
        this.jdbc = jdbc;
        this.tickets = tickets;
        this.store = store;
        this.snapshots = snapshots;
        this.extractor = extractor;
        this.processes = processes;
        this.human = human;
        this.policy = policy;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Batch retrieve(String workspace, UUID queryId, Instant windowStart) {
        return retrieveBounded(workspace, queryId, windowStart, policy.candidateLimit(), false, null, null);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Batch retrievePreflight(String workspace, UUID queryId, Instant windowStart, String room, String objectKey) {
        return retrieveBounded(workspace, queryId, windowStart, 500, true, room, objectKey);
    }

    private Batch retrieveBounded(String workspace, UUID queryId, Instant windowStart, int scanLimit, boolean enhanced, String room, String objectKey) {
        var ticket = tickets.getForIntelligence(workspace, queryId);
        var actor = human.currentActor(workspace);
        String creator = actor.hasRole("agent", "coordinator", "assessment-worker") ? null : actor.subject();
        var parameters = new java.util.ArrayList<Object>(java.util.Arrays.asList(workspace, queryId, creator, creator,
                Timestamp.from(windowStart), Timestamp.from(ticket.createdAt())));
        if (enhanced) parameters.addAll(java.util.Arrays.asList(room, objectKey, room));
        parameters.add(scanLimit);
        var rows = jdbc.query(
                "select t.id,t.creator,t.process_instance_id,t.created_at,t.title,t.description,t.context_revision,t.content_revision "
                        + "from servicedesk_ticket t "
                        + (enhanced ? "left join servicedesk_duplicate_context p on p.ticket_id=t.id and p.context_revision=t.context_revision " : "")
                        + "where t.workspace=? and t.id<>? "
                        + "and (cast(? as varchar) is null or t.creator=?) and t.created_at>=? and t.created_at<=? "
                        + "order by " + (enhanced ? "case when p.room=? and p.object_key=? then 0 when p.room=? then 1 else 2 end," : "")
                        + "t.created_at desc,t.id desc limit ?",
                (row, number) -> new Row(
                        row.getObject("id", UUID.class), row.getString("creator"),
                        row.getString("process_instance_id"), row.getTimestamp("created_at").toInstant(),
                        new TicketContextStore.TicketState(row.getObject("id", UUID.class),
                                row.getString("title"), row.getString("description"), row.getLong("context_revision"),row.getLong("content_revision"))),
                parameters.toArray());
        var states = states(workspace, rows);
        var active = rows.stream().filter(row -> open(workspace, row, states.get(row.processId()))).toList();
        var selected = active.stream().limit(policy.candidateLimit()).toList();
        var events = store.eventsBatch(workspace, selected.stream().map(Row::id).toList());
        var candidates = selected.stream()
                .map(row -> {
                    var snapshot = snapshots.assemble(workspace, row.state(), events.getOrDefault(row.id(), List.of()));
                    return new Candidate(row, snapshot, extractor.extract(snapshot), states.get(row.processId()));
                }).toList();
        String digest = ContextPolicyCatalog.digest(policy.bundleDigest() + ":" + workspace + ":"
                + windowStart + ":" + candidates.stream()
                        .map(candidate -> candidate.snapshot().digest() + ":" + candidate.workflow()).toList());
        return new Batch(candidates, rows.size(), rows.size() == scanLimit || active.size() > selected.size(), digest);
    }

    public Instant windowStart() {
        return Instant.now().minus(policy.horizonDays(), ChronoUnit.DAYS);
    }

    public Instant windowStartAt(Instant end) { return end.minus(policy.horizonDays(), ChronoUnit.DAYS); }

    private Map<String, ProcessSnapshot> states(String workspace, List<Row> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        return processes.snapshots(workspace, rows.stream().map(Row::processId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProcessSnapshot::id, state -> state));
    }

    private static boolean open(String workspace, Row row, ProcessSnapshot state) {
        return ServiceDeskProcessState.isOpen(workspace, row.id(), row.creator(), row.processId(), state);
    }
}
