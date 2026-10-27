package vn.haui.dx.hpdi.human.web;

import vn.haui.dx.hpdi.human.api.AgentDuty;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}/duty")
public class AgentDutyController {
    private final AgentDuty duty;
    public AgentDutyController(AgentDuty duty) { this.duty = duty; }
    @GetMapping
    public List<AgentDuty.Status> list(@PathVariable String workspace) { return duty.list(workspace); }
    @PutMapping("/{subject}")
    public AgentDuty.Status set(@PathVariable String workspace, @PathVariable String subject,
                                      @Valid @RequestBody AgentDuty.Change change) {
        return duty.set(workspace, subject, change);
    }
}
