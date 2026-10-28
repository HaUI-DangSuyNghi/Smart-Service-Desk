package vn.haui.dx.hpdi.settings.web;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.settings.internal.WorkSettings;

@RestController
@RequestMapping("/api/hpdi/v1/settings/work")
public class WorkSettingsController {
    private final WorkSettings settings;
    public WorkSettingsController(WorkSettings settings) { this.settings = settings; }
    @GetMapping public ObjectNode get() { return settings.get(); }
    @PutMapping(consumes = "application/json") public ObjectNode put(@RequestBody String body) { return settings.replace(body); }
}
