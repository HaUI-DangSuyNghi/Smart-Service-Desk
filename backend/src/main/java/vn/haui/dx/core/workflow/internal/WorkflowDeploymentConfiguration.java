package vn.haui.dx.core.workflow.internal;

import java.util.List;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class WorkflowDeploymentConfiguration {
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void validateProcesses(org.springframework.boot.context.event.ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(ProcessRegistry.class).policies();
    }
    @Bean EngineConfigurationConfigurer<SpringProcessEngineConfiguration> contentAwareDeployment() {
        return configuration -> configuration.setDeploymentStrategies(List.of(new ContentAwareDeploymentStrategy()));
    }
}
