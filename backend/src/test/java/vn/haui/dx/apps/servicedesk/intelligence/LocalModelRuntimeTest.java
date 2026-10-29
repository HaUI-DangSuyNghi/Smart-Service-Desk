package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LocalModelRuntimeTest {
    private LocalModelRuntime local(String endpoint, String... profiles) {
        var environment = new StandardEnvironment();
        environment.setActiveProfiles(profiles);
        return new LocalModelRuntime("LOCAL_DEV", List.of("lab-a"), endpoint, environment);
    }

    @Test void explicitLocalProfileAndWorkspaceAreRequired() {
        assertThrows(IllegalStateException.class, () -> local("http://intelligence-classification:8003"));
        assertThrows(IllegalStateException.class, () -> local("http://intelligence-classification:8003", "local-dev", "production"));
        assertThrows(IllegalStateException.class, () -> local("http://intelligence-classification:8003", "local-dev", "prod"));
        var runtime = local("http://intelligence-classification:8003", "local-dev");
        assertTrue(runtime.enabled());
        assertEquals(runtime.authorize("lab-a", "CLASSIFIER", "pin"), runtime.authorize("lab-a", "CLASSIFIER", "pin"));
        assertNotEquals(runtime.authorize("lab-a", "CLASSIFIER", "pin"), runtime.authorize("lab-a", "CLASSIFIER", "changed"));
        assertThrows(CapabilityException.class, () -> runtime.authorize("lab-b", "CLASSIFIER", "pin"));
        assertFalse(LocalModelRuntime.external().enabled());
    }

    @Test void localModeCannotSendInputsToAnArbitraryHost() {
        for (String endpoint : List.of("https://example.com:8003", "http://example.com:8003", "http://localhost:8003@evil.example:8003",
                "http://intelligence-classification:8003/other", "http://intelligence-classification:8003?redirect=evil", "http://intelligence-classification:9000")) {
            assertThrows(IllegalStateException.class, () -> local(endpoint, "local-dev"));
        }
    }

    @Test void localProcessingDoesNotForgeExternalGrantsAndCannotPromoteModels() {
        var settings = new ClassifierConfiguration.Settings(ClassifierConfiguration.Mode.ASSISTED, "QWEN", "a".repeat(64), "b".repeat(64), "", "");
        var json = new ObjectMapper();
        assertThrows(CapabilityException.class, () -> new ClassifierProcessingGate(settings, json).verify("lab-a"));
        var runtime = local("http://intelligence-classification:8003", "local-dev");
        var gate = new ClassifierProcessingGate(settings, json, runtime);
        assertTrue(gate.verifyAssessment("lab-a", "c".repeat(64)).matches("[a-f0-9]{64}"));
        assertThrows(CapabilityException.class, () -> gate.verify("lab-b"));
        assertThrows(CapabilityException.class, () -> gate.verifyAssessment("lab-a", "invalid"));
        var promoted = new ClassifierConfiguration.Settings(ClassifierConfiguration.Mode.PROMOTED, "QWEN", "a".repeat(64), "b".repeat(64), "", "");
        assertThrows(CapabilityException.class, () -> new ClassifierProcessingGate(promoted, json, runtime).verify("lab-a"));
    }
}
