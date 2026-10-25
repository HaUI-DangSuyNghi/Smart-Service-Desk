package vn.haui.dx.apps.servicedesk.assistance;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.haui.dx.hpdi.intelligence.api.AssistanceInput;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Configuration
public class ServiceDeskAssistanceConfiguration {
    public static final String POLICY = "servicedesk-assistance@0.1.0";

    public record Settings(
            boolean enabled, Map<String, List<AssistanceInput.SourcePin>> catalogs, String mode) {
        public Settings {
            var copy = new java.util.HashMap<String, List<AssistanceInput.SourcePin>>();
            catalogs.forEach((workspace, pins) -> copy.put(workspace, List.copyOf(pins)));
            catalogs = Map.copyOf(copy);
        }

        public Settings(boolean enabled, Map<String, List<AssistanceInput.SourcePin>> catalogs) {
            this(enabled, catalogs, enabled ? "synthetic-reference" : "disabled");
        }
    }

    @Bean
    Settings serviceDeskAssistanceSettings(
            @Value("${serviceDesk.assistance.enabled:false}") boolean enabled,
            @Value("${serviceDesk.assistance.mode:disabled}") String mode,
            @Value("${serviceDesk.assistance.source-pins:}") String sourcePins,
            ObjectMapper json) {

        if (!enabled) return new Settings(false, Map.of());

        if (!java.util.Set.of("synthetic-reference", "synthetic-cloud-dev").contains(mode)
                || sourcePins.length() > 16000) {
            throw new IllegalStateException(
                    "Only the explicit synthetic assistance deployment is available");
        }

        try {
            var strict =
                    json.copy()
                            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            Map<String, List<AssistanceInput.SourcePin>> catalogs =
                    strict.readValue(sourcePins, new TypeReference<>() {});

            if (catalogs == null || catalogs.isEmpty() || catalogs.size() > 10)
                throw new IllegalArgumentException();

            for (var entry : catalogs.entrySet()) {
                if (!entry.getKey().matches("[a-zA-Z0-9_-]{1,100}")
                        || entry.getValue() == null
                        || entry.getValue().isEmpty()
                        || entry.getValue().size() > 10) throw new IllegalArgumentException();

                var identifiers = new HashSet<>();

                for (var pin : entry.getValue()) {
                    if (pin == null
                            || pin.metadataId() == null
                            || !identifiers.add(pin.metadataId())
                            || pin.version() == null
                            || pin.version().isBlank()
                            || pin.version().length() > 100
                            || pin.checksum() == null
                            || !pin.checksum().matches("[a-f0-9]{64}")
                            || !("synthetic-cloud-dev".equals(mode)
                                            ? "synthetic-public-evaluation"
                                            : "synthetic-test-only")
                                    .equals(pin.usageRights()))
                        throw new IllegalArgumentException();
                }
            }

            return new Settings(true, catalogs, mode);
        } catch (IOException | IllegalArgumentException error) {
            throw new IllegalStateException("Invalid synthetic assistance source pins");
        }
    }

    public static Map<String, String> referenceIdentity() {
        return Map.of(
                "provider", "citation-bound-template",
                "provider_version", "0.1.0",
                "model", "none",
                "model_revision", "none",
                "config_version", "exact-excerpts@0.1.0",
                "prompt_envelope_version", "data-only@0.1.0",
                "app_prompt_version", POLICY,
                "output_schema_version", "grounded-claims@0.1.0");
    }
}
