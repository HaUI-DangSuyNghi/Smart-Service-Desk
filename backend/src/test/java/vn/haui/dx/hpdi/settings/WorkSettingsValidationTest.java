package vn.haui.dx.hpdi.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.settings.api.SettingsValidation;
import vn.haui.dx.hpdi.settings.internal.WorkSettingsValidation;
import static org.junit.jupiter.api.Assertions.*;

class WorkSettingsValidationTest {
    private final ObjectMapper json = new ObjectMapper();
    private ObjectNode seed() throws Exception {
        String sql = new String(getClass().getResourceAsStream("/db/migration/hpdi/V32__work_settings.sql").readAllBytes(), StandardCharsets.UTF_8);
        ObjectNode seed = SettingsValidation.parse(json, sql.substring(sql.indexOf("'{") + 1, sql.lastIndexOf("}',") + 1));
        seed.put("version", 1); return seed;
    }
    @Test void seedAndBoundaryRatiosValidate() throws Exception {
        ObjectNode seed = seed(); WorkSettingsValidation.validate(seed);
        seed.withObject("assignment").put("pendingLoadFactor", 0);
        seed.withObject("sla").put("escalateAfterRatio", 1); WorkSettingsValidation.validate(seed);
    }
    @ParameterizedTest @ValueSource(strings={
        "/version|-1", "/version|1.5", "/version|9223372036854775808", "/version|\"1\"",
        "/assignment|null", "/assignment/pendingLoadFactor|1.1", "/assignment/tieThresholdMinutes|-1",
        "/assignment/maxInProgress|0", "/assignment/minRemainingMinutes|2147483648",
        "/shifts|[]", "/shifts/0/name|\" \"", "/shifts/0/start|\"7:00\"", "/shifts/0/end|\"06:00\"",
        "/sla/targets/LOW/startWithinMinutes|0", "/sla/targets/LOW/resolveWithinMinutes|1",
        "/sla/maxEscalatedPriority|\"URGENT\"", "/sla/waitingExternalCounts|\"true\"",
        "/critical/autoQueueAfterMinutes|0", "/alerts/warnRemainingRatio|-0.1", "/alerts/pendingRemindDays|0",
        "/alerts/reopenUnclaimedMinutes|0", "/closing/autoCloseAfterDays|0", "/estimate/minSamples|0",
        "/estimate/windowMonths|0", "/estimate/outlierMinMinutes|-1", "/estimate/outlierMaxMinutes|1",
        "/estimate/unknown|1", "/updatedAt|\"2026-10-28T00:00:00Z\""
    })
    void rejectsInvalidNestedFields(String mutation) throws Exception {
        ObjectNode seed = seed(); String[] parts = mutation.split("\\|", 2);
        int last = parts[0].lastIndexOf('/');
        ((ObjectNode) seed.at(parts[0].substring(0, last))).set(parts[0].substring(last + 1), json.readTree(parts[1]));
        assertThrows(CapabilityException.class, () -> WorkSettingsValidation.validate(seed));
    }
    @Test void rejectsDuplicateTrailingAndMissingFields() throws Exception {
        assertThrows(CapabilityException.class, () -> SettingsValidation.parse(json, "{\"version\":1,\"version\":2}"));
        assertThrows(CapabilityException.class, () -> SettingsValidation.parse(json, "{} {}"));
        ObjectNode seed = seed(); seed.withObject("assignment").remove("maxInProgress");
        assertThrows(CapabilityException.class, () -> WorkSettingsValidation.validate(seed));
    }
}
