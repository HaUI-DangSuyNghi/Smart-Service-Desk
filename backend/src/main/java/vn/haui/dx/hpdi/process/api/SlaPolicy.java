package vn.haui.dx.hpdi.process.api;
import java.time.*;
import java.util.*;

public record SlaPolicy(String version, BusinessCalendar calendar, Map<Priority, Target> targets,
                        double escalateAfterRatio, Priority maxEscalatedPriority, boolean waitingExternalCounts) {
    public enum Priority { LOW, MEDIUM, HIGH, CRITICAL }
    public record Target(long startMinutes, long resolveMinutes, boolean continuous) {
        public Target {
            if (startMinutes < 1 || resolveMinutes < startMinutes || resolveMinutes > 5256000)
                throw new IllegalArgumentException("Invalid SLA target");
        }
    }
    public SlaPolicy {
        if (version == null || version.isBlank() || calendar == null || maxEscalatedPriority == null
                || !Double.isFinite(escalateAfterRatio) || escalateAfterRatio < 0 || escalateAfterRatio > 1)
            throw new IllegalArgumentException("Invalid SLA policy");
        targets = Map.copyOf(targets);
        if (!targets.keySet().equals(Set.of(Priority.values()))) throw new IllegalArgumentException("Missing SLA targets");
    }
    public boolean pauses(String reason) {
        return "WAITING_REQUESTER".equals(reason) || (!waitingExternalCounts && Set.of("WAITING_PARTS", "WAITING_EXTERNAL").contains(reason == null ? "" : reason));
    }
    public Priority escalate(Priority base, Instant approvedAt, Instant firstStarted, Instant now) {
        if (firstStarted != null || now.isBefore(approvedAt) || base.ordinal() >= maxEscalatedPriority.ordinal()) return base;
        var elapsed = calendar.between(approvedAt, now, targets.get(base).continuous());
        return elapsed.toMillis() > targets.get(base).startMinutes() * 60000.0 * escalateAfterRatio
            ? Priority.values()[base.ordinal() + 1] : base;
    }
    public Sla project(Priority base, Instant startBy, Instant resolveBy, Instant firstStarted,
                       Instant resolvedAt, Instant pausedAt, Instant now) {
        var target = targets.get(base);
        var current = pausedAt != null && now.isAfter(pausedAt)
            ? calendar.add(resolveBy, calendar.between(pausedAt, now, target.continuous()), target.continuous()) : resolveBy;
        var startCheck = firstStarted == null ? now : firstStarted;
        var resolveCheck = resolvedAt == null ? now : resolvedAt;
        boolean startBreach = startCheck.isAfter(startBy);
        boolean resolveBreach = resolveCheck.isAfter(current);
        boolean warning = firstStarted == null && !startBreach && near(now, startBy, target.startMinutes(), target.continuous())
            || resolvedAt == null && !resolveBreach && near(now, current, target.resolveMinutes(), target.continuous());
        return new Sla(startBy, current, startBreach, resolveBreach, warning,
            resolvedAt == null && (pausedAt != null || !target.continuous() && !calendar.working(now)));
    }
    private boolean near(Instant now, Instant deadline, long budgetMinutes, boolean continuous) {
        return !now.isAfter(deadline) && calendar.between(now, deadline, continuous).toMillis() < budgetMinutes * 60000.0 * 0.2;
    }
}
