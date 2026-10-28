package vn.haui.dx.hpdi.settings.internal;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import static vn.haui.dx.hpdi.settings.api.SettingsValidation.*;

public final class WorkSettingsValidation {
    private WorkSettingsValidation() {}
    public static void validate(JsonNode root) {
        object(root, "version", "assignment", "shifts", "sla", "critical", "alerts", "closing", "estimate");
        integer(root.get("version"), 0, Long.MAX_VALUE);
        var assignment = root.get("assignment");
        object(assignment, "pendingLoadFactor", "tieThresholdMinutes", "maxInProgress", "minRemainingMinutes");
        ratio(assignment.get("pendingLoadFactor"));
        integer(assignment.get("tieThresholdMinutes"), 0, Integer.MAX_VALUE);
        // Core chỉ nhận 1..100 việc Đang làm
        integer(assignment.get("maxInProgress"), 1, 100);
        integer(assignment.get("minRemainingMinutes"), 0, Integer.MAX_VALUE);
        var shifts = root.get("shifts");
        require(shifts.isArray() && !shifts.isEmpty(), "At least one shift required");
        var names = new HashSet<String>();
        var intervals = new ArrayList<String[]>();
        for (var shift : shifts) {
            object(shift, "name", "start", "end");
            require(names.add(text(shift.get("name"), 50)), "Duplicate shift name");
            String start = text(shift.get("start"), 5), end = text(shift.get("end"), 5);
            require(start.matches("([01][0-9]|2[0-3]):[0-5][0-9]")
                && end.matches("([01][0-9]|2[0-3]):[0-5][0-9]") && start.compareTo(end) < 0, "Invalid shift interval");
            intervals.add(new String[]{start, end});
        }
        intervals.sort(Comparator.comparing(interval -> interval[0]));
        for (int index = 1; index < intervals.size(); index++)
            require(intervals.get(index - 1)[1].compareTo(intervals.get(index)[0]) <= 0, "Overlapping shifts");
        var sla = root.get("sla");
        object(sla, "targets", "escalateAfterRatio", "maxEscalatedPriority", "waitingExternalCounts");
        object(sla.get("targets"), "LOW", "MEDIUM", "HIGH", "CRITICAL");
        for (var target : sla.get("targets")) {
            object(target, "startWithinMinutes", "resolveWithinMinutes");
            long start = integer(target.get("startWithinMinutes"), 1, Integer.MAX_VALUE);
            // Trần của SlaPolicy.Target (10 năm)
            integer(target.get("resolveWithinMinutes"), start, 5_256_000);
        }
        ratio(sla.get("escalateAfterRatio"));
        require(Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(text(sla.get("maxEscalatedPriority"), 8)), "Invalid priority");
        require(sla.get("waitingExternalCounts").isBoolean(), "Boolean required");
        object(root.get("critical"), "autoQueueAfterMinutes");
        positive(root.get("critical").get("autoQueueAfterMinutes"));
        var alerts = root.get("alerts");
        object(alerts, "warnRemainingRatio", "pendingRemindDays", "reopenUnclaimedMinutes");
        ratio(alerts.get("warnRemainingRatio")); positive(alerts.get("pendingRemindDays")); positive(alerts.get("reopenUnclaimedMinutes"));
        object(root.get("closing"), "autoCloseAfterDays"); positive(root.get("closing").get("autoCloseAfterDays"));
        var estimate = root.get("estimate");
        object(estimate, "minSamples", "windowMonths", "outlierMinMinutes", "outlierMaxMinutes");
        positive(estimate.get("minSamples")); positive(estimate.get("windowMonths"));
        long minimum = integer(estimate.get("outlierMinMinutes"), 0, Integer.MAX_VALUE);
        integer(estimate.get("outlierMaxMinutes"), Math.max(1, minimum), Integer.MAX_VALUE);
    }
}
