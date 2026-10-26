package vn.haui.dx.apps.servicedesk.intelligence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClassifierConfiguration {
    public enum Mode {
        DISABLED,
        SHADOW,
        ASSISTED,
        PROMOTED
    }

    public record Settings(
            Mode mode,
            String provider,
            String modelDigest,
            String policyDigest,
            String processingReceipt,
            String processingVerificationKey) {}

    @Bean
    Settings classifierSettings(
            @Value("${serviceDesk.classifier.mode:DISABLED}") Mode mode,
            @Value("${serviceDesk.classifier.provider:}") String provider,
            @Value("${serviceDesk.classifier.model-digest:}") String modelDigest,
            @Value("${serviceDesk.classifier.policy-digest:}") String policyDigest,
            @Value("${serviceDesk.classifier.processing-receipt:}") String receipt,
            @Value("${serviceDesk.classifier.processing-verification-key:}") String key) {
        return new Settings(mode, provider, modelDigest, policyDigest, receipt, key);
    }
}
