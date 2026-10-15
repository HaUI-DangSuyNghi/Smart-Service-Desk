package vn.haui.dx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CoreApplication {
    public static void main(String[] args) { SpringApplication.run(CoreApplication.class, args); }

    // Flowable chỉ deploy BPMN trong pha lifecycle cuối, nên phải kiểm sau khi ứng dụng sẵn sàng
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void validateProcesses(org.springframework.boot.context.event.ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(vn.haui.dx.core.workflow.internal.ProcessRegistry.class).policies();
    }
}
