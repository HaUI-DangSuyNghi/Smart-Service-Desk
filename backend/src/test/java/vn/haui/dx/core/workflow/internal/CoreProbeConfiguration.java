package vn.haui.dx.core.workflow.internal;
import org.springframework.context.annotation.*;
import vn.haui.dx.core.workflow.api.ProcessRegistration;
/** Test-only contributions for the standalone technical Core proof. */
@Configuration
@Profile("core-only")
public class CoreProbeConfiguration {
    @Bean ProcessRegistration coreProbeProcesses() { return new ApprovalProcesses(); }
}
