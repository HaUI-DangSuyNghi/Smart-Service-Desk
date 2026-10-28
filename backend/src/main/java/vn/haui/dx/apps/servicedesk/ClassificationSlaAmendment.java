package vn.haui.dx.apps.servicedesk;

import java.time.Duration;
import java.time.Instant;
import vn.haui.dx.hpdi.process.api.SlaPolicy;

final class ClassificationSlaAmendment {
    record Deadlines(Instant startBy, Instant resolveBy, long startAdjustmentMillis, long resolveAdjustmentMillis) {}
    private record Converted(Instant deadline, long adjustmentMillis) {}

    static Deadlines calculate(SlaPolicy policy, SlaPolicy.Priority previous, SlaPolicy.Priority next,
            Instant startBy, Instant resolveBy, Instant firstStarted, Instant pausedAt, Instant now,
            long startAdjustmentMillis, long resolveAdjustmentMillis) {
        var oldTarget = policy.targets().get(previous);
        var newTarget = policy.targets().get(next);
        var resolutionAt = pausedAt == null ? now : pausedAt;
        var resolve = convert(policy, resolveBy, resolutionAt, oldTarget.resolveMinutes(),
            newTarget.resolveMinutes(), oldTarget.continuous(), newTarget.continuous(), resolveAdjustmentMillis);
        var start = firstStarted == null ? convert(policy, startBy, now, oldTarget.startMinutes(),
            newTarget.startMinutes(), oldTarget.continuous(), newTarget.continuous(), startAdjustmentMillis)
            : new Converted(startBy, startAdjustmentMillis);
        if (start.deadline().isAfter(resolve.deadline())) {
            long adjustment = start.adjustmentMillis()
                - signedBetween(policy, resolve.deadline(), start.deadline(), newTarget.continuous()).toMillis();
            start = new Converted(resolve.deadline(), adjustment);
        }
        return new Deadlines(start.deadline(), resolve.deadline(), start.adjustmentMillis(), resolve.adjustmentMillis());
    }

    private static Converted convert(SlaPolicy policy, Instant deadline, Instant at, long oldMinutes,
            long newMinutes, boolean oldContinuous, boolean newContinuous, long adjustmentMillis) {
        var oldBudget = Duration.ofMinutes(oldMinutes);
        var consumed = oldBudget.plus(signedBetween(policy, deadline, at, oldContinuous)).plusMillis(adjustmentMillis);
        if (consumed.isNegative()) consumed = Duration.ZERO;
        var remaining = Duration.ofMinutes(newMinutes).minus(consumed);
        var nextDeadline = deadline.isBefore(at) ? deadline
            : remaining.isNegative() || remaining.isZero() ? at : policy.calendar().add(at, remaining, newContinuous);
        long nextAdjustment = consumed.minus(Duration.ofMinutes(newMinutes))
            .minus(signedBetween(policy, nextDeadline, at, newContinuous)).toMillis();
        return new Converted(nextDeadline, nextAdjustment);
    }

    private static Duration signedBetween(SlaPolicy policy, Instant from, Instant to, boolean continuous) {
        return from.isAfter(to) ? policy.calendar().between(to, from, continuous).negated()
            : policy.calendar().between(from, to, continuous);
    }
}
