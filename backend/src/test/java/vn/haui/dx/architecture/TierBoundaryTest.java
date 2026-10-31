package vn.haui.dx.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compiled dependency edges catch fully qualified, inherited and signature leaks. */
class TierBoundaryTest {
    private static final String PREFIX = "vn.haui.dx.";
    private record Edge(String path, String rule, String value) implements Comparable<Edge> {
        @Override public int compareTo(Edge other) {
            return (path + ":" + rule + ":" + value).compareTo(other.path + ":" + other.rule + ":" + other.value);
        }
    }

    private static List<String> rules(String origin, String target) {
        var rules = new ArrayList<String>();
        boolean core = origin.startsWith(PREFIX + "core.");
        boolean hpdi = origin.startsWith(PREFIX + "hpdi.");
        boolean app = origin.startsWith(PREFIX + "apps.");
        if (core && (target.startsWith(PREFIX + "hpdi.") || target.startsWith(PREFIX + "apps.") || target.startsWith(PREFIX + "assembly."))) {
            rules.add("core-upper-dependency");
        }
        if (hpdi && target.startsWith(PREFIX + "apps.")) rules.add("hpdi-app-dependency");
        if ((app || hpdi) && target.startsWith(PREFIX + "assembly.")) rules.add("lower-tier-assembly-dependency");
        if (app && target.startsWith(PREFIX + "core.")) rules.add("app-core-dependency");
        if (hpdi && target.startsWith(PREFIX + "core.")) {
            if (origin.matches("vn[.]haui[.]dx[.]hpdi[.][^.]+[.]api[.].*")
                    || !target.matches("vn[.]haui[.]dx[.]core[.](?:[^.]+[.]api|common)[.].*")) {
                rules.add("hpdi-private-core-dependency");
            }
        }
        if (app && target.startsWith(PREFIX + "hpdi.")
                && !target.matches("vn[.]haui[.]dx[.]hpdi[.][^.]+[.]api[.].*")) {
            rules.add("app-private-hpdi-dependency");
        }
        if (target.startsWith("org.flowable.") && !origin.startsWith(PREFIX + "core.workflow.internal.")) {
            rules.add("flowable-outside-internal");
        }
        if (app && (target.startsWith("java.net.http.") || target.startsWith("org.springframework.web.client.")
                || target.startsWith("com.networknt.schema."))) rules.add("app-transport-dependency");
        return rules;
    }

    private static Path root() {
        var path = Path.of("").toAbsolutePath();
        while (path != null && !Files.isRegularFile(path.resolve("docs/architecture/boundary-debt.json"))) {
            path = path.getParent();
        }
        if (path == null) throw new IllegalStateException("Cannot locate repository boundary baseline");
        return path;
    }

    @Test void dependencyDirectionWithExactExpiringDebt() throws Exception {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("vn.haui.dx");
        assertFalse(classes.isEmpty(), "Architecture gate must import production classes");
        Map<Edge, Integer> observed = new TreeMap<>();
        for (var type : classes) {
            for (var dependency : type.getDirectDependenciesFromSelf()) {
                String origin = dependency.getOriginClass().getName();
                String target = dependency.getTargetClass().getName();
                // Each exception is a single class-to-class edge, never a package.
                String file = "backend/src/main/java/" + origin.split("\\$", 2)[0].replace('.', '/') + ".java";
                for (String rule : rules(origin, target)) {
                    observed.put(new Edge(file, rule, origin + " -> " + target), 1);
                }
            }
        }
        var mapper = new ObjectMapper();
        Path root = root();
        // Inventory is explicit, diagnostic only, and never used by CI.
        if (Boolean.getBoolean("boundary.inventory")) {
            var inventory = observed.entrySet().stream().map(item -> Map.of(
                "path", item.getKey().path(), "rule", item.getKey().rule(),
                "value", item.getKey().value(), "occurrences", item.getValue())).toList();
            Path output = root.resolve("backend/target/boundary-java-inventory.json");
            Files.createDirectories(output.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), inventory);
            System.out.println("INVENTORY ONLY: " + output + " (not an enforcement result)");
            return;
        }
        var baseline = mapper.readTree(root.resolve("docs/architecture/boundary-debt.json").toFile());
        var errors = new ArrayList<String>();
        assertTrue(baseline.path("schemaVersion").asInt() == 1 && baseline.path("java").isArray(), "Invalid baseline schema");
        if ("strict".equals(baseline.path("mode").asText()) && !baseline.path("java").isEmpty())
            errors.add("Strict mode permits no migration exceptions");
        Map<Edge, JsonNode> known = new TreeMap<>();
        var ids = new java.util.HashSet<String>();
        for (JsonNode entry : baseline.path("java")) {
            String id = entry.path("id").asText();
            for (String field : List.of("id", "path", "rule", "value", "owner", "targetPhase", "expires")) {
                if (!entry.path(field).isTextual() || entry.path(field).asText().isBlank()) errors.add("Incomplete debt " + id + ": " + field);
            }
            String file = entry.path("path").asText();
            if (!file.startsWith("backend/src/main/java/") || file.contains("..") || file.contains("\\")
                    || file.matches(".*[?*\\[\\]:].*")) errors.add("Non-exact path " + id);
            if (!entry.path("targetPhase").asText().matches("BND-0[1-7]")) errors.add("Invalid removal phase " + id);
            try {
                if (!LocalDate.parse(entry.path("expires").asText()).isAfter(LocalDate.now())) errors.add("Expired debt " + id);
            } catch (java.time.format.DateTimeParseException exception) {
                errors.add("Invalid expiry " + id);
            }
            if (entry.path("occurrences").asInt() != 1) errors.add("Each Java debt entry must name one exact edge: " + id);
            var edge = new Edge(file, entry.path("rule").asText(), entry.path("value").asText());
            if (known.put(edge, entry) != null || !ids.add(id)) errors.add("Duplicate debt " + id);
        }
        for (var edge : observed.keySet()) {
            if (!known.containsKey(edge)) errors.add("New boundary debt: " + edge);
        }
        for (var item : known.entrySet()) {
            if (!observed.containsKey(item.getKey())) errors.add("Remove stale debt " + item.getValue().path("id").asText());
        }
        assertTrue(errors.isEmpty(), () -> String.join("\n", errors));
    }
}
