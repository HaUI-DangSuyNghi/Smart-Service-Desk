package vn.haui.dx.apps.servicedesk.intelligence.context;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContextIntelligenceConfiguration {
    public enum Mode { DISABLED, SHADOW, ASSISTED, PROMOTED }

    public record Settings(
            boolean enabled, Mode mode, String policyBundleDigest,
            String processingReceipt, String verificationKey) {}

    @Bean
    Settings contextIntelligenceSettings(
            @Value("${serviceDesk.context.enabled:false}") boolean enabled,
            @Value("${serviceDesk.context.mode:DISABLED}") Mode mode,
            @Value("${serviceDesk.context.policy-bundle-digest:}") String digest,
            @Value("${serviceDesk.context.processing-receipt:}") String receipt,
            @Value("${serviceDesk.context.verification-key:}") String key) {
        return new Settings(enabled, mode, digest, receipt, key);
    }
}
