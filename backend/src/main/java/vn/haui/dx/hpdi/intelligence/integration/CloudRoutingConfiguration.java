package vn.haui.dx.hpdi.intelligence.integration;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.haui.dx.hpdi.intelligence.api.AssistanceRouting;

import java.time.Clock;

@Configuration
public class CloudRoutingConfiguration {
    @Bean
    AssistanceRouting assistanceRouting(
            @Value("${PROVIDER_MODE:disabled}") String mode,
            @Value("${PROVIDER_CATALOG_PATH:}") String catalog,
            @Value("${PROVIDER_CATALOG_SHA256:}") String digest,
            @Value("${CLOUD_EGRESS_APPROVAL_FILE:}") String approval,
            @Value("${CLOUD_EGRESS_APPROVAL_SHA256:}") String approvalDigest,
            @Value("${CLOUD_ISOLATION_FILE:}") String isolation,
            @Value("${CLOUD_ENVIRONMENT:disabled}") String environment,
            ObjectMapper json) {
        if ("disabled".equals(mode)) return new GovernedAssistanceRouting(null, Clock.systemUTC());
        if (!"synthetic-cloud-dev".equals(mode))
            throw new IllegalStateException("Unsupported assistance provider mode");
        return new GovernedAssistanceRouting(
                new CloudRouteCatalog(
                        catalog,
                        digest,
                        approval,
                        approvalDigest,
                        isolation,
                        environment,
                        json,
                        Clock.systemUTC()),
                Clock.systemUTC());
    }
}
