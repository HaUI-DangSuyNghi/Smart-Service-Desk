package vn.haui.dx.apps.roomrequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/apps/roomrequest/v1/workspaces/{workspace}/requests")
public class RoomRequestController {
    private final RoomRequests requests;
    public RoomRequestController(RoomRequests requests) { this.requests=requests; }
    public record NewRoomRequest(String room,UUID knowledgeId) {}
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public RoomRequest create(@PathVariable String workspace,@RequestBody NewRoomRequest input) {
        if (input == null) throw vn.haui.dx.hpdi.common.api.CapabilityException.validation("Room request is required");
        return requests.create(workspace,input.room(),input.knowledgeId());
    }
    @GetMapping("/{id}")
    public RoomRequest get(@PathVariable String workspace,@PathVariable UUID id) { return requests.get(workspace,id); }
    @GetMapping("/{id}/progress")
    public RoomProgress timeline(@PathVariable String workspace,@PathVariable UUID id) { return requests.timeline(workspace,id); }
    @PostMapping("/{id}/suggestion")
    public RoomSuggestion suggest(@PathVariable String workspace,@PathVariable UUID id, jakarta.servlet.http.HttpServletRequest request) throws java.io.IOException {
        if (request.getInputStream().read() != -1) throw vn.haui.dx.hpdi.common.api.CapabilityException.validation("Suggestion does not accept a request body");
        return requests.suggest(workspace,id);
    }
}
