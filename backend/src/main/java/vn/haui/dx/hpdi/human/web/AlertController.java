package vn.haui.dx.hpdi.human.web;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.human.api.WorkAlerts;

@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}/alerts")
public class AlertController {
    private final WorkAlerts alerts;
    public AlertController(WorkAlerts alerts) { this.alerts = alerts; }
    @GetMapping
    public WorkAlerts.Inbox list(@PathVariable String workspace, @RequestParam(required = false) Instant since,
            @RequestParam(defaultValue = "false") boolean unreadOnly, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return alerts.list(workspace, since, unreadOnly, PageRequest.of(page, size));
    }
    @PostMapping("/{alertId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable String workspace, @PathVariable UUID alertId) { alerts.read(workspace, alertId); }
}
