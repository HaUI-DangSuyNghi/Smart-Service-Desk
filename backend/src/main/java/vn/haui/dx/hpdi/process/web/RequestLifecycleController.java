package vn.haui.dx.hpdi.process.web;

import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.process.internal.RequestLifecycleService;

@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}/instances/{id}/request")
public class RequestLifecycleController {
    private final RequestLifecycleService service;
    public RequestLifecycleController(RequestLifecycleService service) { this.service=service; }
    @GetMapping public RequestLifecycleService.View get(@PathVariable String workspace,@PathVariable String id) { return service.read(workspace,id); }
    @PatchMapping public RequestLifecycleService.View update(@PathVariable String workspace,@PathVariable String id,
            @RequestBody RequestLifecycleService.Update request) { return service.update(workspace,id,request); }
    @PostMapping("/cancel") public RequestLifecycleService.View cancel(@PathVariable String workspace,@PathVariable String id,
            @RequestBody RequestLifecycleService.Cancel request) { return service.cancel(workspace,id,request); }
    @PostMapping("/close") public RequestLifecycleService.View close(@PathVariable String workspace,@PathVariable String id,
            @RequestBody RequestLifecycleService.Cancel request) { return service.close(workspace,id,request); }
}
