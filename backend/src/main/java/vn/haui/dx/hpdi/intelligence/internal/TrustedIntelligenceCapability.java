package vn.haui.dx.hpdi.intelligence.internal;

import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceCapability;
import vn.haui.dx.hpdi.intelligence.api.TriageInput;
import vn.haui.dx.hpdi.intelligence.api.TriageSuggestion;
import vn.haui.dx.hpdi.intelligence.integration.IntelligenceTransport;
import vn.haui.dx.hpdi.intelligence.integration.TriageRequest;
import vn.haui.dx.hpdi.intelligence.integration.TriageValidator;

@Service
public class TrustedIntelligenceCapability implements IntelligenceCapability {
    private final HumanContext human;
    private final IntelligenceTransport transport;
    private final TriageValidator validator;
    private final ObjectMapper json;
    public TrustedIntelligenceCapability(HumanContext human, IntelligenceTransport transport, TriageValidator validator, ObjectMapper json) {
        this.human = human; this.transport = transport; this.validator = validator; this.json = json;
    }
    public TriageSuggestion suggest(String workspace, TriageInput input) {
        var actor = human.currentActor(workspace);
        if (actor == null || !workspace.equals(actor.workspace()) || actor.subject() == null || actor.subject().isBlank())
            throw CapabilityException.forbidden("Trusted workspace actor required");
        if (input == null || input.version() == null || input.version().isBlank() || input.version().length() > 100)
            throw CapabilityException.validation("An immutable profile version is required");
        var request = new TriageRequest("0.1.0", UUID.randomUUID().toString(), input.profile(),
            new TriageRequest.Context(actor.workspace()), new TriageRequest.Input(input.title(), input.description()));
        var envelope = new TriageRequest.Envelope(request,
            new TriageRequest.TrustedContext(actor.workspace(), actor.subject()), input.version());
        validator.request(envelope);
        var response = transport.triage(envelope);
        validator.response(envelope, response);
        return new TriageSuggestion(json.convertValue(response, new TypeReference<Map<String, Object>>() {}));
    }
}
