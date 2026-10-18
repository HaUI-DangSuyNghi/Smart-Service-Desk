package vn.haui.dx.hpdi.human.web;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.human.api.*;
@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}")
public class HumanController {
    private final HumanContext human;
    public HumanController(HumanContext human) { this.human = human; }
    @GetMapping("/actor")
    public Actor actor(@PathVariable String workspace) { return human.currentActor(workspace); }
}
