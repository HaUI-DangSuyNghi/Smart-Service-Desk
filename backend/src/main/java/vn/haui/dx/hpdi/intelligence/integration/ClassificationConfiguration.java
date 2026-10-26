package vn.haui.dx.hpdi.intelligence.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClassificationConfiguration {
    public record Settings(String baseUrl, String token) {}

    @Bean
    Settings classificationSettings(
            @Value("${intelligence.classification.base-url:}") String baseUrl,
            @Value("${intelligence.classification.token:}") String token) {
        return new Settings(baseUrl, token);
    }
}
