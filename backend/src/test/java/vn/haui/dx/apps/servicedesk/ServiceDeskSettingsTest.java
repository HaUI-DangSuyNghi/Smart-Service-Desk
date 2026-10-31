package vn.haui.dx.apps.servicedesk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.settings.api.SettingsValidation;
import static org.junit.jupiter.api.Assertions.*;

class ServiceDeskSettingsTest {
    private final ObjectMapper json = new ObjectMapper();
    private ObjectNode seed() throws Exception {
        String sql = new String(getClass().getResourceAsStream("/db/migration/apps/servicedesk/V33__servicedesk_settings.sql").readAllBytes(), StandardCharsets.UTF_8);
        ObjectNode seed = SettingsValidation.parse(json, sql.substring(sql.indexOf("'{") + 1, sql.lastIndexOf("}',") + 1));
        seed.put("version", 1); return seed;
    }
    @Test void seedMatchesEveryRuntimeEstimateWithoutWiring() throws Exception {
        ObjectNode seed = seed(); ServiceDeskSettings.validate(seed);
        for (var category : TicketReview.Category.values())
            for (var priority : TicketReview.Priority.values())
                assertEquals(TicketReview.defaultMinutes(category, priority), seed.get("estimateDefaults").get(category.name()).get(priority.name()).asInt());
    }
    @Test void sampleDataIsOptionalBoolean() throws Exception {
        ObjectNode seed = seed();
        seed.put("sampleData", true);
        ServiceDeskSettings.validate(seed);
        seed.put("sampleData", false);
        ServiceDeskSettings.validate(seed);
    }
    @ParameterizedTest @ValueSource(strings={
        "/version|1.2", "/version|9223372036854775808", "/version|-1", "/categoryGroups|null",
        "/categoryGroups/OTHER|\"hardware\"", "/categoryGroups/HARDWARE|null",
        "/categoryGroups/NETWORK|\"coordinator\"", "/categoryGroups/SOFTWARE|\"../admin\"",
        "/categoryGroups/ACCOUNT|\"platform-admin\"", "/categoryGroups/EXTRA|\"network\"",
        "/estimateDefaults/OTHER/LOW|0", "/estimateDefaults/NETWORK/MEDIUM|10081",
        "/estimateDefaults/HARDWARE/HIGH|1.5", "/estimateDefaults/SOFTWARE/CRITICAL|\"20\"",
        "/estimateDefaults/ACCOUNT/EXTRA|1", "/updatedBy|\"forged\"", "/sampleData|\"true\"", "/sampleData|null"
    })
    void rejectsInvalidReplacement(String mutation) throws Exception {
        ObjectNode seed = seed(); String[] parts = mutation.split("\\|", 2);
        int last = parts[0].lastIndexOf('/');
        ((ObjectNode) seed.at(parts[0].substring(0, last))).set(parts[0].substring(last + 1), json.readTree(parts[1]));
        assertThrows(CapabilityException.class, () -> ServiceDeskSettings.validate(seed));
    }
}
