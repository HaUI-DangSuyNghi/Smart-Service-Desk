package vn.haui.dx.hpdi.process.web;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.process.internal.EstimateRequests;

@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}")
public class EstimateRequestController {
    private final EstimateRequests requests;
    public EstimateRequestController(EstimateRequests requests) { this.requests = requests; }
    public record Proposal(com.fasterxml.jackson.databind.JsonNode proposedMinutes, com.fasterxml.jackson.databind.JsonNode reason) {}
    public record Decision(com.fasterxml.jackson.databind.JsonNode approved, com.fasterxml.jackson.databind.JsonNode note) {}

    @PostMapping("/tasks/{id}/estimate-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public EstimateRequests.Request create(@PathVariable String workspace, @PathVariable String id, @RequestBody Proposal body) {
        var minutes = body.proposedMinutes();
        if (minutes == null || !minutes.isIntegralNumber() || !minutes.canConvertToInt() || body.reason() == null || !body.reason().isTextual())
            throw vn.haui.dx.hpdi.common.api.CapabilityException.validation("Integer proposedMinutes and string reason required");
        return requests.create(workspace, id, minutes.intValue(), body.reason().textValue());
    }
    @GetMapping("/estimate-requests")
    public Page<EstimateRequests.Request> list(@PathVariable String workspace,
            @RequestParam(required = false) EstimateRequests.Status status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return requests.list(workspace, status, PageRequest.of(page, size));
    }
    @PostMapping("/estimate-requests/{requestId}/decision")
    public EstimateRequests.Request decide(@PathVariable String workspace, @PathVariable UUID requestId, @RequestBody Decision body) {
        if (body.approved() == null || !body.approved().isBoolean() || body.note() != null && !body.note().isTextual())
            throw vn.haui.dx.hpdi.common.api.CapabilityException.validation("Boolean approved and optional string note required");
        return requests.decide(workspace, requestId, body.approved().booleanValue(), body.note() == null ? null : body.note().textValue());
    }
}
