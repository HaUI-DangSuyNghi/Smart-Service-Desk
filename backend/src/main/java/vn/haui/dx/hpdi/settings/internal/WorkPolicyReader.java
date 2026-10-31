package vn.haui.dx.hpdi.settings.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.stream.StreamSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.haui.dx.core.workflow.api.ExecutionLimits;
import vn.haui.dx.hpdi.settings.api.WorkPolicy;

/**
 * Đọc hpdi_work_settings cho runtime (đã validate lúc lưu). Giữ trong bộ nhớ ngắn hạn vì được gọi
 * trong vòng lặp (hàng đợi, SLA, đề xuất); lưu mới thì xóa ngay, máy chủ khác thấy sau tối đa TTL.
 */
@Service
public class WorkPolicyReader implements WorkPolicy, ExecutionLimits {
    // Hết hạn theo giờ máy thật (không theo Clock nghiệp vụ, test có thể cố định Clock)
    private static final long TTL_NANOS = Duration.ofSeconds(5).toNanos();
    private record Cached(Snapshot snapshot, long loadedAt) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private volatile Cached cached;

    public WorkPolicyReader(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc; this.json = json;
    }

    @Override
    public Snapshot current() {
        var value = cached;
        long now = System.nanoTime();
        if (value != null && now - value.loadedAt() < TTL_NANOS) return value.snapshot();
        var snapshot = jdbc.query("select version,payload from hpdi_work_settings where id=1",
            (row, index) -> parse(row.getLong("version"), row.getString("payload"))).stream().findFirst().orElse(DEFAULTS);
        cached = new Cached(snapshot, now);
        return snapshot;
    }

    @Override
    public int maxInProgress() { return current().maxInProgress(); }

    /** Gọi khi lưu cấu hình: xóa bản nhớ sau khi commit để không ai nạp lại bản cũ giữa chừng. */
    void invalidate() {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            cached = null;
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { cached = null; }
            });
    }

    private Snapshot parse(long version, String payload) {
        JsonNode root;
        try { root = json.readTree(payload); }
        catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException("Invalid work settings", failure); }
        var assignment = root.path("assignment");
        var sla = root.path("sla");
        var alerts = root.path("alerts");
        var estimate = root.path("estimate");
        List<Shift> shifts = StreamSupport.stream(root.path("shifts").spliterator(), false)
            .map(shift -> new Shift(shift.path("name").asText(), LocalTime.parse(shift.path("start").asText()),
                LocalTime.parse(shift.path("end").asText())))
            .toList();
        var targets = new HashMap<String, Target>();
        sla.path("targets").fields().forEachRemaining(entry -> targets.put(entry.getKey(),
            new Target(entry.getValue().path("startWithinMinutes").asInt(), entry.getValue().path("resolveWithinMinutes").asInt())));
        return new Snapshot(version,
            assignment.path("pendingLoadFactor").asDouble(), assignment.path("tieThresholdMinutes").asInt(),
            assignment.path("maxInProgress").asInt(), assignment.path("minRemainingMinutes").asInt(),
            shifts, targets, sla.path("escalateAfterRatio").asDouble(), sla.path("maxEscalatedPriority").asText(),
            sla.path("waitingExternalCounts").asBoolean(),
            root.path("critical").path("autoQueueAfterMinutes").asInt(),
            alerts.path("warnRemainingRatio").asDouble(), alerts.path("pendingRemindDays").asInt(),
            alerts.path("reopenUnclaimedMinutes").asInt(), root.path("closing").path("autoCloseAfterDays").asInt(),
            estimate.path("minSamples").asInt(), estimate.path("windowMonths").asInt(),
            estimate.path("outlierMinMinutes").asInt(), estimate.path("outlierMaxMinutes").asInt());
    }
}
