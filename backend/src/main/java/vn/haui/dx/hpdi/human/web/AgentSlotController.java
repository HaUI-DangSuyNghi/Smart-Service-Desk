package vn.haui.dx.hpdi.human.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.human.api.AgentSlots;

/** Đăng ký ca theo tuần: xem ô, đăng ký / huỷ (của mình hoặc hộ), duyệt / từ chối. */
@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}/schedules")
public class AgentSlotController {
    private final AgentSlots slots;
    public AgentSlotController(AgentSlots slots) { this.slots=slots; }

    @GetMapping("/policy")
    public AgentSlots.Policy policy(@PathVariable String workspace) { return slots.policy(workspace); }

    @GetMapping("/slots")
    public List<AgentSlots.Slot> list(@PathVariable String workspace,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        return slots.list(workspace,from,to);
    }

    @PostMapping("/{subject}/slots")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentSlots.Slot register(@PathVariable String workspace, @PathVariable String subject,
            @RequestBody AgentSlots.Registration registration) {
        return slots.register(workspace,subject,registration);
    }

    @DeleteMapping("/{subject}/slots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String workspace, @PathVariable String subject, @PathVariable UUID id) {
        slots.cancel(workspace,subject,id);
    }

    @PostMapping("/slots/{id}/approve")
    public AgentSlots.Slot approve(@PathVariable String workspace, @PathVariable UUID id) { return slots.approve(workspace,id); }

    @PostMapping("/slots/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable String workspace, @PathVariable UUID id) { slots.reject(workspace,id); }

    @GetMapping("/work-modes")
    public List<AgentSlots.WorkMode> workModes(@PathVariable String workspace) { return slots.workModes(workspace); }

    @PutMapping("/{subject}/work-mode")
    public AgentSlots.WorkMode setWorkMode(@PathVariable String workspace, @PathVariable String subject,
            @RequestBody AgentSlots.WorkModeChange change) {
        return slots.setWorkMode(workspace,subject,change);
    }
}
