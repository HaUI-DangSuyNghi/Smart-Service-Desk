package vn.haui.dx.apps.servicedesk;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.process.api.ProcessObservations;
import vn.haui.dx.hpdi.process.api.ProcessSnapshot;

/**
 * Lọc danh sách ticket theo trạng thái R6, ưu tiên, loại, người xử lý, quá hạn (servicedesk-target.yaml).
 * Trạng thái không lưu sẵn nên tính theo lô như thống kê, rồi mới cắt trang.
 */
@Service
class TicketSearchService {
    private static final Set<String> STATES = Arrays.stream(TicketStatisticsService.Status.values())
        .map(Enum::name).collect(Collectors.toUnmodifiableSet());
    private static final List<String> PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> CATEGORIES = Set.of("HARDWARE", "NETWORK", "SOFTWARE", "ACCOUNT", "OTHER");
    private static final int BATCH = 1000;

    /** assignee đã đổi "me" thành subject; prioritySort: thứ tự R4 thay cho mới nhất trước. */
    record Filter(Set<String> states, Set<String> priorities, String category, String assignee, Boolean overdue,
                  boolean prioritySort) {
        static Filter of(List<String> states, List<String> priorities, String category, String assignee, Boolean overdue,
                         String sort, String subject) {
            var stateSet = values(states, STATES, "Invalid ticket state");
            var prioritySet = values(priorities, Set.copyOf(PRIORITIES), "Invalid priority");
            if (category != null && !CATEGORIES.contains(category)) throw CapabilityException.validation("Invalid category");
            if (assignee != null && (assignee.isBlank() || assignee.length() > 100
                    || assignee.codePoints().anyMatch(Character::isISOControl)))
                throw CapabilityException.validation("Invalid assignee");
            if (sort != null && !Set.of("createdAt", "priority").contains(sort)) throw CapabilityException.validation("Invalid sort");
            return new Filter(stateSet, prioritySet, category, "me".equals(assignee) ? subject : assignee, overdue,
                "priority".equals(sort));
        }

        private static Set<String> values(List<String> values, Set<String> allowed, String error) {
            if (values == null) return Set.of();
            if (values.size() > allowed.size() || !allowed.containsAll(values)) throw CapabilityException.validation(error);
            return Set.copyOf(values);
        }
    }

    private record Match(UUID id, Instant createdAt, boolean inProgress, int rank, Instant deadline) {}

    private static final Comparator<Match> NEWEST = Comparator.comparing(Match::createdAt).reversed()
        .thenComparing(Match::id, Comparator.reverseOrder());
    // R4 như hàng đợi: việc Đang làm ghim đầu → ưu tiên hiệu lực → hạn SLA gần nhất → tạo trước
    private static final Comparator<Match> QUEUE_ORDER = Comparator.<Match, Boolean>comparing(Match::inProgress, Comparator.reverseOrder())
        .thenComparing(Comparator.comparingInt(Match::rank).reversed())
        .thenComparing(Match::deadline, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(Match::createdAt).thenComparing(Match::id);

    private final TicketRepository tickets;
    private final ProcessObservations processes;
    private final TicketSlaService sla;

    TicketSearchService(TicketRepository tickets, ProcessObservations processes, TicketSlaService sla) {
        this.tickets = tickets; this.processes = processes; this.sla = sla;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<Ticket> search(String workspace, String creator, String query, Instant from, Instant to,
                               Filter filter, PageRequest page) {
        var matches = new ArrayList<Match>();
        Instant now = sla.now();
        UUID after = null;
        while (true) {
            var batch = tickets.filterBatch(workspace, creator, query, from, to, after, BATCH);
            if (batch.isEmpty()) break;
            var snapshots = processes.snapshots(workspace, batch.stream().map(row -> row.link().processId()).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProcessSnapshot::id, snapshot -> snapshot));
            var slas = sla.batch(workspace, snapshots.values().stream().flatMap(snapshot -> snapshot.tasks().stream())
                .filter(task -> "handle".equals(task.key()) && task.id() != null).map(ProcessSnapshot.TaskState::id).toList());
            for (var row : batch) {
                var match = match(workspace, row, snapshots.get(row.link().processId()), slas, filter, now);
                if (match != null) matches.add(match);
            }
            after = batch.get(batch.size() - 1).link().id();
        }
        matches.sort(filter.prioritySort() ? QUEUE_ORDER : NEWEST);
        int start = (int) Math.min(page.offset(), matches.size());
        int end = Math.min(start + page.size(), matches.size());
        var ids = matches.subList(start, end).stream().map(Match::id).toList();
        return new Page<>(tickets.findAll(workspace, ids), page.page(), page.size(), matches.size());
    }

    private static Match match(String workspace, TicketRepository.FilterRow row, ProcessSnapshot snapshot,
                               Map<String, TicketSlaService.State> slas, Filter filter, Instant now) {
        var status = TicketStatisticsService.classify(workspace, row.link(), snapshot);
        if (!filter.states().isEmpty() && !filter.states().contains(status.name())) return null;
        if (filter.category() != null && !filter.category().equals(row.category())) return null;
        var task = snapshot == null || snapshot.tasks().size() != 1 ? null : snapshot.tasks().get(0);
        if (filter.assignee() != null && (task == null || !filter.assignee().equals(task.assignee()))) return null;
        var state = task != null && "handle".equals(task.key()) ? slas.get(task.id()) : null;
        if (state != null && !(row.link().id().equals(state.ticketId()) && row.link().processId().equals(state.processId())))
            state = null;
        boolean overdue = state != null && TicketStatisticsService.currentlyOverdue(state, now);
        if (filter.overdue() != null && filter.overdue() != overdue) return null;
        // Ưu tiên hiệu lực (đã nâng bậc) khi đang xử lý, còn lại lấy theo lần duyệt gần nhất
        String priority = state != null ? state.priority().name() : row.priority();
        if (!filter.priorities().isEmpty() && (priority == null || !filter.priorities().contains(priority))) return null;
        Instant deadline = null;
        if (state != null && state.resolvedAt() == null) {
            var projected = state.project(now);
            deadline = state.firstStarted() == null ? projected.startBy() : projected.resolveBy();
        }
        return new Match(row.link().id(), row.createdAt(), status == TicketStatisticsService.Status.IN_PROGRESS,
            priority == null ? 0 : PRIORITIES.indexOf(priority) + 1, deadline);
    }
}
