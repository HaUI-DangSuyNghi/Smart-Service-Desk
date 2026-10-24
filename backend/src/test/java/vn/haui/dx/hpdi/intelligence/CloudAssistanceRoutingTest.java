package vn.haui.dx.hpdi.intelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.intelligence.api.AssistanceRoute;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;
import vn.haui.dx.hpdi.intelligence.integration.CloudRouteCatalog;
import vn.haui.dx.hpdi.intelligence.integration.GovernedAssistanceRouting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

class CloudAssistanceRoutingTest {
    @TempDir Path directory;

    private final ObjectMapper json = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC);
    private GovernedAssistanceRouting routing;
    private CloudRouteCatalog catalog;

    @BeforeEach
    void setup() throws Exception {
        var config = json.createObjectNode();
        config.put("apiVersion", "cloud-selection@0.2.0");
        config.put("mode", "synthetic-cloud-dev");
        config.put("policyId", "synthetic-assistance");
        config.put("profileId", "service-desk");
        config.put("profileVersion", "0.3.0");
        config.put("allowPaidUsage", false);
        config.put("maxAttempts", 1);
        config.set(
                "policy",
                json.valueToTree(
                        Map.of(
                                "identity",
                                "servicedesk-assistance@0.1.0",
                                "instructions",
                                "Synthetic test only",
                                "max_claims",
                                3,
                                "needs_review",
                                true)));

        var routes = config.putArray("routes");

        for (String vendor : List.of("gemini", "groq")) {
            var route = routes.addObject();
            route.put("id", vendor + "-route");
            route.put("vendor", vendor);
            route.put("model", "fixture-" + vendor);
            route.putArray("observedModels").add("fixture-" + vendor);
            route.put("priority", vendor.equals("gemini") ? 10 : 20);
            route.put("maxInputBytes", 16000);
            route.put("maxOutputTokens", 256);
            route.put("timeoutSeconds", 3);
            route.put("rpm", 2);
            route.put("rpd", 10);
            route.put("tpm", 200000);
            route.put("tpd", 1000000);
        }

        byte[] catalogBytes = json.writeValueAsBytes(config);
        String digest = CloudRouteCatalog.hash(catalogBytes);
        Files.write(directory.resolve("catalog.json"), catalogBytes);

        var approval = json.createObjectNode();
        approval.put("schemaVersion", "cloud-egress@0.2.0");
        approval.put("status", "APPROVED");
        approval.put("owner", "TEST_ONLY_NOT_A_REAL_APPROVAL");
        approval.put("environmentId", "isolated-fixture");
        approval.put("expiresAt", "2026-10-25T00:00:00Z");
        approval.put("catalogDigest", digest);
        approval.put("dataClass", CloudRouteCatalog.DATA_CLASS);
        approval.putArray("actors").add("actor");
        approval.putArray("questionDigests").add("a".repeat(64));
        approval.putArray("sourceDigests").add("b".repeat(64));
        approval.putArray("resources")
                .addObject()
                .put("workspace", "scope-a")
                .put("id", "resource")
                .put("contentDigest", "c".repeat(64));

        byte[] approvalBytes = json.writeValueAsBytes(approval);
        Files.write(directory.resolve("approval.json"), approvalBytes);
        Files.write(
                directory.resolve("isolation.json"),
                json.writeValueAsBytes(
                        Map.of(
                                "schemaVersion",
                                "isolated-synthetic@0.1.0",
                                "environmentId",
                                "isolated-fixture",
                                "catalogDigest",
                                digest,
                                "databasePurpose",
                                "synthetic-only",
                                "production",
                                false)));

        catalog =
                new CloudRouteCatalog(
                        directory.resolve("catalog.json").toString(),
                        digest,
                        directory.resolve("approval.json").toString(),
                        CloudRouteCatalog.hash(approvalBytes),
                        directory.resolve("isolation.json").toString(),
                        "isolated-synthetic",
                        json,
                        clock);
        routing = new GovernedAssistanceRouting(catalog, clock);
    }

    private AssistanceRoute select() {
        return routing.select("synthetic-assistance", CloudRouteCatalog.DATA_CLASS);
    }

    @Test
    void cooldownSwitchesOnlyFollowingRequestWithFreshPinnedIdentity() {
        var first = select();
        assertEquals("gemini-route", first.routeId());

        routing.feedback(first, "RATE_LIMITED", "PROVIDER_RATE_LIMITED", 60);
        var next = select();

        assertEquals("groq-route", next.routeId());
        assertNotEquals(first.attemptId(), next.attemptId());
        assertNotEquals(first.expectedRuntime(), next.expectedRuntime());
        assertThrows(IntelligenceFailure.class, () -> routing.validate(first));
    }

    @Test
    void disabledCredentialsAndExhaustedPoolFailClosed() {
        var first = select();
        routing.feedback(first, "DISABLED", "PROVIDER_CONFIGURATION_ERROR", 0);
        var second = select();
        routing.feedback(second, "UNAVAILABLE", "GENERATION_TIMEOUT", 60);

        assertThrows(IntelligenceFailure.class, this::select);
    }

    @Test
    void conservativeQuotaCannotBeResetByReleasingAttempts() {
        routing.release(select());
        routing.release(select());
        assertEquals("groq-route", select().routeId());
        routing.release(select());

        assertThrows(IntelligenceFailure.class, this::select);
    }

    @Test
    void modifiedBindingAndReplayedAttemptAreRejected() {
        var issued = select();
        var forged =
                new AssistanceRoute(
                        "groq-route",
                        issued.policyId(),
                        issued.dataClass(),
                        issued.catalogDigest(),
                        issued.routeDigest(),
                        issued.approvalDigest(),
                        issued.attemptId(),
                        issued.expectedRuntime());

        assertThrows(IntelligenceFailure.class, () -> routing.validate(forged));
        routing.validate(issued);
        routing.release(issued);
        assertThrows(IntelligenceFailure.class, () -> routing.validate(issued));
    }

    @Test
    void actorResourceQuestionAndEverySourceMustBeApproved() {
        routing.authorize(
                "scope-a",
                "actor",
                "resource",
                "c".repeat(64),
                "a".repeat(64),
                Set.of("b".repeat(64)));

        for (String[] values :
                List.of(
                        new String[] {"scope-b", "actor", "resource", "c", "a", "b"},
                        new String[] {"scope-a", "foreign", "resource", "c", "a", "b"},
                        new String[] {"scope-a", "actor", "changed-resource", "c", "a", "b"},
                        new String[] {"scope-a", "actor", "resource", "d", "a", "b"},
                        new String[] {"scope-a", "actor", "resource", "c", "d", "b"},
                        new String[] {"scope-a", "actor", "resource", "c", "a", "d"})) {
            assertThrows(
                    CapabilityException.class,
                    () ->
                            routing.authorize(
                                    values[0],
                                    values[1],
                                    values[2],
                                    values[3].repeat(64),
                                    values[4].repeat(64),
                                    Set.of(values[5].repeat(64))));
        }
    }

    @Test
    void changedApprovalCannotIssueAnotherAttempt() throws Exception {
        Files.writeString(directory.resolve("approval.json"), "{}");

        assertThrows(IntelligenceFailure.class, this::select);
        assertThrows(
                IntelligenceFailure.class,
                () ->
                        new GovernedAssistanceRouting(null, clock)
                                .select("synthetic-assistance", CloudRouteCatalog.DATA_CLASS));
    }

    @Test
    void expiredReceiptCannotStartRuntime() {
        var tomorrow = Clock.fixed(Instant.parse("2026-10-25T00:00:00Z"), ZoneOffset.UTC);

        assertThrows(
                IntelligenceFailure.class,
                () ->
                        new CloudRouteCatalog(
                                directory.resolve("catalog.json").toString(),
                                catalog.digest,
                                directory.resolve("approval.json").toString(),
                                catalog.approvalDigest,
                                directory.resolve("isolation.json").toString(),
                                "isolated-synthetic",
                                json,
                                tomorrow));
    }
}
