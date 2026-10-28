package vn.haui.dx.core.workflow.internal;
import org.springframework.context.annotation.*;
import vn.haui.dx.core.workflow.api.ProcessRegistration;
/** Test-only contributions when production sample applications are not compiled. */
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${dx.proof.core-only:false} or ${dx.proof.second-domain:false}")
public class CoreProbeConfiguration {
    @Bean ProcessRegistration coreProbeProcesses() { return new ApprovalProcesses(); }
}
