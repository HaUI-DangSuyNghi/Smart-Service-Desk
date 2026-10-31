package vn.haui.dx.apps.servicedesk;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/comments")
public class TicketCommentController {
    private final TicketCommentService comments;

    public TicketCommentController(TicketCommentService comments) { this.comments = comments; }

    @GetMapping
    public List<TicketCommentService.Comment> list(@PathVariable String workspace, @PathVariable UUID id) {
        return comments.list(workspace, id);
    }

    @PostMapping
    @ResponseStatus(CREATED)
    public TicketCommentService.Comment add(@PathVariable String workspace, @PathVariable UUID id,
                                            @RequestBody TicketCommentService.NewComment input) {
        return comments.add(workspace, id, input);
    }
}
