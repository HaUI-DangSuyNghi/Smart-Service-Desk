package vn.haui.dx.apps.servicedesk.intake;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/canonical")
public class CanonicalTicketController {
    private final CanonicalTicketService service;
    public CanonicalTicketController(CanonicalTicketService service) { this.service = service; }
    @GetMapping
    public CanonicalTicketService.View get(@PathVariable String workspace, @PathVariable UUID id) { return service.view(workspace, id); }
    @PostMapping
    public CanonicalTicketService.View link(@PathVariable String workspace, @PathVariable UUID id,
            @RequestBody CanonicalTicketService.Link command) { return service.link(workspace, id, command); }
    @PostMapping("/split")
    public CanonicalTicketService.View split(@PathVariable String workspace, @PathVariable UUID id,
            @RequestBody CanonicalTicketService.Split command) { return service.split(workspace, id, command); }
}
