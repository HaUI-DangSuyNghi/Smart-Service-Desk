package vn.haui.dx.apps.servicedesk;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}/duty")
public class AgentDutyController {
    private final AgentDutyService duty;
    public AgentDutyController(AgentDutyService duty) { this.duty = duty; }
    @GetMapping
    public List<AgentDutyService.Status> list(@PathVariable String workspace) { return duty.list(workspace); }
    @PutMapping("/{subject}")
    public AgentDutyService.Status set(@PathVariable String workspace, @PathVariable String subject,
                                      @Valid @RequestBody AgentDutyService.Change change) {
        return duty.set(workspace, subject, change);
    }
}
