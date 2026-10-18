package vn.haui.dx.core.workflow.internal;

import java.util.List;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class WorkflowDeploymentConfiguration {
    @Bean EngineConfigurationConfigurer<SpringProcessEngineConfiguration> contentAwareDeployment() {
        return configuration -> configuration.setDeploymentStrategies(List.of(new ContentAwareDeploymentStrategy()));
    }
}
