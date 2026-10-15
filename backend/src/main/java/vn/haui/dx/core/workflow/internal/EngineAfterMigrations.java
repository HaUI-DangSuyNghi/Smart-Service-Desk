package vn.haui.dx.core.workflow.internal;

import java.util.Arrays;
import java.util.stream.Stream;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

/**
 * Flowable tự tạo bảng act_* khi khởi tạo engine. Nếu chạy trước Flyway, database mới sẽ "không rỗng"
 * và Flyway từ chối migrate → backend không khởi động. Buộc engine phụ thuộc Flyway để luôn chạy sau.
 */
@Component
class EngineAfterMigrations implements BeanFactoryPostProcessor {
    static final String FLYWAY = "flywayInitializer";

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) {
        if (!factory.containsBeanDefinition(FLYWAY)) return;
        for (String name : factory.getBeanNamesForType(SpringProcessEngineConfiguration.class, true, false)) {
            var definition = factory.getBeanDefinition(name);
            String[] current = definition.getDependsOn() == null ? new String[0] : definition.getDependsOn();
            if (Arrays.asList(current).contains(FLYWAY)) continue;
            definition.setDependsOn(Stream.concat(Arrays.stream(current), Stream.of(FLYWAY)).toArray(String[]::new));
        }
    }
}
