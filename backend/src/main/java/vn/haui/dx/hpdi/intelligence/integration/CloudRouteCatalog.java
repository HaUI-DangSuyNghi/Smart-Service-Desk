package vn.haui.dx.hpdi.intelligence.integration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Operator-controlled immutable files, separate from application fixtures. */
public class CloudRouteCatalog {
    public static final String DATA_CLASS = "synthetic-public-evaluation";

    public record Route(
            String id,
            String vendor,
            String model,
            int priority,
            int maxInputBytes,
            int maxOutputTokens,
            int rpm,
            int rpd,
            long tpm,
            long tpd) {}

    private final Path catalogPath;
    private final Path approvalPath;
    private final Path isolationPath;
    private final ObjectMapper json;
    private final Clock clock;
    public final String digest;
    public final String approvalDigest;
    public final String isolationDigest;
    public final String policyId;
    public final String promptVersion;
    public final JsonNode approval;
    public final List<Route> routes;

    public CloudRouteCatalog(
            String catalogPath,
            String digest,
            String approvalPath,
            String approvalDigest,
            String isolationPath,
            String environment,
            ObjectMapper json,
            Clock clock) {
        this.catalogPath = Path.of(catalogPath).toAbsolutePath().normalize();
        this.approvalPath = Path.of(approvalPath).toAbsolutePath().normalize();
        this.isolationPath = Path.of(isolationPath).toAbsolutePath().normalize();
        this.digest = digest;
        this.approvalDigest = approvalDigest;
        this.clock = clock;
        this.json =
                json.copy()
                        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

        try {
            if (!"isolated-synthetic".equals(environment)
                    || this.approvalPath.toRealPath().startsWith(Path.of("").toRealPath())
                    || this.isolationPath.toRealPath().startsWith(Path.of("").toRealPath()))
                throw new IllegalArgumentException();

            var config = read(this.catalogPath, digest);
            approval = read(this.approvalPath, approvalDigest);

            if (!Files.isRegularFile(this.isolationPath, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(this.isolationPath) > 131072) {
                throw new IllegalArgumentException();
            }

            isolationDigest = hash(Files.readAllBytes(this.isolationPath));
            var isolation = read(this.isolationPath, isolationDigest);
            fields(
                    config,
                    Set.of(
                            "apiVersion",
                            "mode",
                            "policyId",
                            "profileId",
                            "profileVersion",
                            "policy",
                            "allowPaidUsage",
                            "maxAttempts",
                            "routes"));
            if (!"cloud-selection@0.2.0".equals(config.path("apiVersion").asText())
                    || !"synthetic-cloud-dev".equals(config.path("mode").asText())
                    || !config.path("allowPaidUsage").isBoolean()
                    || config.path("allowPaidUsage").booleanValue()
                    || !config.path("maxAttempts").isInt()
                    || config.path("maxAttempts").intValue() != 1)
                throw new IllegalArgumentException();

            policyId = text(config, "policyId");
            promptVersion = text(config.get("policy"), "identity");
            fields(
                    approval,
                    Set.of(
                            "schemaVersion",
                            "status",
                            "owner",
                            "environmentId",
                            "expiresAt",
                            "catalogDigest",
                            "dataClass",
                            "questionDigests",
                            "sourceDigests",
                            "actors",
                            "resources"));
            if (!"cloud-egress@0.2.0".equals(approval.path("schemaVersion").asText())
                    || !"APPROVED".equals(approval.path("status").asText())
                    || !digest.equals(approval.path("catalogDigest").asText())
                    || !DATA_CLASS.equals(approval.path("dataClass").asText()))
                throw new IllegalArgumentException();
            text(approval, "owner");
            String environmentId = text(approval, "environmentId");
            fields(
                    isolation,
                    Set.of(
                            "schemaVersion",
                            "environmentId",
                            "catalogDigest",
                            "databasePurpose",
                            "production"));
            if (!"isolated-synthetic@0.1.0".equals(isolation.path("schemaVersion").asText())
                    || !environmentId.equals(isolation.path("environmentId").asText())
                    || !digest.equals(isolation.path("catalogDigest").asText())
                    || !"synthetic-only".equals(isolation.path("databasePurpose").asText())
                    || !isolation.path("production").isBoolean()
                    || isolation.path("production").booleanValue())
                throw new IllegalArgumentException();
            for (String key : List.of("actors", "resources", "questionDigests", "sourceDigests")) {
                if (!approval.path(key).isArray()
                        || approval.path(key).isEmpty()
                        || approval.path(key).size() > 256) throw new IllegalArgumentException();
            }

            for (String key : List.of("questionDigests", "sourceDigests")) {
                for (var value : approval.get(key)) {
                    if (!value.isTextual() || !value.textValue().matches("[a-f0-9]{64}")) {
                        throw new IllegalArgumentException();
                    }
                }
            }

            for (var value : approval.get("actors")) {
                if (!value.isTextual()
                        || value.textValue().isBlank()
                        || value.textValue().length() > 100) {
                    throw new IllegalArgumentException();
                }
            }

            for (var resource : approval.get("resources")) {
                fields(resource, Set.of("workspace", "id", "contentDigest"));
                text(resource, "workspace");
                text(resource, "id");

                if (!text(resource, "contentDigest").matches("[a-f0-9]{64}")) {
                    throw new IllegalArgumentException();
                }
            }

            var entries = config.get("routes");
            if (entries == null || !entries.isArray() || entries.isEmpty() || entries.size() > 2)
                throw new IllegalArgumentException();
            var selected = new ArrayList<Route>();
            var vendors = new java.util.HashSet<String>();
            var identifiers = new java.util.HashSet<String>();
            for (var entry : entries) {
                fields(
                        entry,
                        Set.of(
                                "id",
                                "vendor",
                                "model",
                                "observedModels",
                                "priority",
                                "maxInputBytes",
                                "maxOutputTokens",
                                "timeoutSeconds",
                                "rpm",
                                "rpd",
                                "tpm",
                                "tpd"));
                String id = text(entry, "id");
                String vendor = text(entry, "vendor");
                String model = text(entry, "model");
                if (!id.matches("[a-z][a-z0-9-]{0,63}")
                        || !identifiers.add(id)
                        || !Set.of("gemini", "groq").contains(vendor)
                        || !vendors.add(vendor)
                        || !model.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,160}")
                        || model.startsWith("REQUIRED")) throw new IllegalArgumentException();

                integer(entry, "timeoutSeconds", 1, 12);
                var observed = entry.path("observedModels");

                if (!observed.isArray() || observed.isEmpty() || observed.size() > 10) {
                    throw new IllegalArgumentException();
                }

                for (var value : observed) {
                    if (!value.isTextual()
                            || value.textValue().isBlank()
                            || value.textValue().length() > 200
                            || value.textValue().startsWith("REQUIRED")) {
                        throw new IllegalArgumentException();
                    }
                }

                long reservation =
                        (long) integer(entry, "maxInputBytes", 256, 64000)
                                + integer(entry, "maxOutputTokens", 32, 1024)
                                + 1024;

                if (integer(entry, "tpm", 256, 100000000) < reservation
                        || integer(entry, "tpd", 256, 1000000000) < reservation) {
                    throw new IllegalArgumentException();
                }
                selected.add(
                        new Route(
                                id,
                                vendor,
                                model,
                                integer(entry, "priority", 0, 1000),
                                integer(entry, "maxInputBytes", 256, 64000),
                                integer(entry, "maxOutputTokens", 32, 1024),
                                integer(entry, "rpm", 1, 10000),
                                integer(entry, "rpd", 1, 100000),
                                integer(entry, "tpm", 256, 100000000),
                                integer(entry, "tpd", 256, 1000000000)));
            }
            routes =
                    selected.stream()
                            .sorted(
                                    Comparator.comparingInt(Route::priority)
                                            .thenComparing(Route::id))
                            .toList();
            validateCurrent();
        } catch (Exception failure) {
            throw IntelligenceFailure.configuration();
        }
    }

    public void validateCurrent() {
        read(catalogPath, digest);
        read(approvalPath, approvalDigest);
        read(isolationPath, isolationDigest);
        if (!clock.instant().isBefore(Instant.parse(approval.path("expiresAt").asText())))
            throw IntelligenceFailure.configuration();
    }

    public Map<String, String> identity(Route route) {
        return Map.of(
                "provider",
                route.vendor() + "-selection",
                "provider_version",
                "0.1.0",
                "model",
                route.model(),
                "model_revision",
                "vendor-unpublished",
                "config_version",
                "cloud-route@0.2.0:" + routeDigest(route),
                "prompt_envelope_version",
                "cloud-selection-data@0.2.0",
                "app_prompt_version",
                promptVersion,
                "output_schema_version",
                "grounded-claims@0.1.0");
    }

    public String routeDigest(Route route) {
        return hash((digest + ":" + route.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw IntelligenceFailure.configuration();
        }
    }

    private JsonNode read(Path path, String expected) {
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(path) > 131072
                    || expected == null
                    || !expected.matches("[a-f0-9]{64}")) throw new IllegalArgumentException();
            byte[] bytes = Files.readAllBytes(path);
            if (!hash(bytes).equals(expected)) throw new IllegalArgumentException();
            return json.readTree(bytes);
        } catch (Exception failure) {
            throw IntelligenceFailure.configuration();
        }
    }

    private static void fields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException();
        var keys = new java.util.HashSet<String>();
        node.fieldNames().forEachRemaining(keys::add);
        if (!keys.equals(expected)) throw new IllegalArgumentException();
    }

    private static String text(JsonNode node, String key) {
        var value = node.get(key);
        if (value == null
                || !value.isTextual()
                || value.textValue().isBlank()
                || value.textValue().length() > 200) throw new IllegalArgumentException();
        return value.textValue();
    }

    private static int integer(JsonNode node, String key, int min, int max) {
        var value = node.get(key);
        if (value == null || !value.isInt() || value.intValue() < min || value.intValue() > max)
            throw new IllegalArgumentException();
        return value.intValue();
    }
}
