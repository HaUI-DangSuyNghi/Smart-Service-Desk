package vn.haui.dx.apps.servicedesk.triage;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.haui.dx.core.common.CoreException;

@RestController
public class TicketTriageController {
    private final TicketTriageService triage;

    public TicketTriageController(TicketTriageService triage) { this.triage = triage; }

    @PostMapping("/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/triage")
    public JsonNode triage(@PathVariable String workspace, @PathVariable UUID id, HttpServletRequest request) throws IOException {
        if (request.getInputStream().read() != -1) throw CoreException.validation("Triage does not accept a request body");
        return triage.triage(workspace, id);
    }
}
