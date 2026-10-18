package vn.haui.dx.apps.roomrequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.process.api.ProcessTimeline;
import vn.haui.dx.hpdi.intelligence.api.TriageSuggestion;
@RestController
@RequestMapping("/api/apps/roomrequest/v1/workspaces/{workspace}/requests")
public class RoomRequestController {
    private final RoomRequests requests;
    public RoomRequestController(RoomRequests requests) { this.requests=requests; }
    public record NewRoomRequest(String room,UUID knowledgeId) {}
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public RoomRequest create(@PathVariable String workspace,@RequestBody NewRoomRequest input) {
        return requests.create(workspace,input.room(),input.knowledgeId());
    }
    @GetMapping("/{id}")
    public RoomRequest get(@PathVariable String workspace,@PathVariable UUID id) { return requests.get(workspace,id); }
    @GetMapping("/{id}/progress")
    public ProcessTimeline timeline(@PathVariable String workspace,@PathVariable UUID id) { return requests.timeline(workspace,id); }
    @PostMapping("/{id}/suggestion")
    public TriageSuggestion suggest(@PathVariable String workspace,@PathVariable UUID id) { return requests.suggest(workspace,id); }
}
