package vn.haui.dx.hpdi.human.web;

import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.human.api.AgentSchedules;

@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}/schedules")
public class AgentScheduleController {
    private final AgentSchedules schedules;
    public AgentScheduleController(AgentSchedules schedules) { this.schedules=schedules; }
    @GetMapping
    public java.util.List<AgentSchedules.Schedule> list(@PathVariable String workspace) { return schedules.list(workspace); }
    @GetMapping("/{subject}")
    public AgentSchedules.Schedule get(@PathVariable String workspace, @PathVariable String subject) {
        return schedules.get(workspace,subject);
    }
    @PutMapping("/{subject}")
    public AgentSchedules.Schedule replace(@PathVariable String workspace, @PathVariable String subject,
            @RequestBody AgentSchedules.Change change) { return schedules.replace(workspace,subject,change); }
    @GetMapping("/{subject}/effective-duty")
    public AgentSchedules.Effective effective(@PathVariable String workspace, @PathVariable String subject) {
        return schedules.effective(workspace,subject);
    }
}
