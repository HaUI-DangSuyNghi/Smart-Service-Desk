package vn.haui.dx.hpdi.intelligence.internal;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.core.common.RequestContext;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.intelligence.api.*;
import vn.haui.dx.hpdi.intelligence.integration.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;

@Service
public class TrustedIntelligenceCapability implements IntelligenceCapability {
    private final HumanContext human;
    private final IntelligenceTransport transport;
    private final ObjectMapper json;
    private final AuditService audit;
    public TrustedIntelligenceCapability(HumanContext human, IntelligenceTransport transport, ObjectMapper json, AuditService audit) {
        this.human=human; this.transport=transport; this.json=json; this.audit=audit;
    }
    @Transactional
    public TriageSuggestion suggest(String workspace, TriageInput input) {
        var actor=human.currentActor(workspace);
        if (input==null || !valid(input.profile(),100) || !valid(input.version(),100)
                || !valid(input.title(),200) || !valid(input.description(),4000))
            throw CapabilityException.validation("Invalid Intelligence input");
        String requestId=RequestContext.requestId().map(Object::toString).orElseGet(()->UUID.randomUUID().toString());
        var request=json.createObjectNode();
        request.put("contractVersion","0.1.0").put("requestId",requestId).put("profile",input.profile());
        request.putObject("context").put("workspaceId",workspace);
        request.putObject("input").put("title",input.title()).put("description",input.description());
        var envelope=json.createObjectNode(); envelope.set("request",request);
        envelope.putObject("trustedContext").put("workspaceId",actor.workspace()).put("subject",actor.subject());
        envelope.put("profileVersion",input.version());
        TriageSuggestion suggestion;
        try { suggestion=verify(transport.triage(envelope), requestId, workspace, input); }
        catch (IntelligenceUnavailable unavailable) { suggestion=TriageSuggestion.unavailable(); }
        audit.record("intelligence.triage", "intelligence.profile", input.profile(), workspace,
            Map.of("profileVersion",input.profile()+"@"+input.version(),"status",suggestion.status()));
        return suggestion;
    }
    private static boolean valid(String value,int max) { return value!=null && !value.isBlank() && value.length()<=max; }
    private static TriageSuggestion verify(JsonNode body,String requestId,String workspace,TriageInput input) {
        require(body!=null && body.isObject());
        require("0.1.0".equals(body.path("contractVersion").asText()) && requestId.equals(body.path("requestId").asText())
            && workspace.equals(body.path("workspaceId").asText()) && input.profile().equals(body.path("profile").asText()));
        require(body.path("needsReview").isBoolean() && body.path("evidence").isArray());
        if ("DEGRADED".equals(body.path("status").asText())) {
            require(body.path("result").isNull() && body.path("runtime").isNull() && body.path("explanation").isNull()
                && body.path("evidence").isEmpty() && body.path("needsReview").asBoolean()
                && "INTELLIGENCE_UNAVAILABLE".equals(body.path("error").path("code").asText()));
            return TriageSuggestion.unavailable();
        }
        require("SUCCESS".equals(body.path("status").asText()) && body.path("error").isNull());
        String identity=input.profile()+"@"+input.version();
        require(identity.equals(body.path("runtime").path("profileVersion").asText()));
        var result=new HashMap<String,String>();
        for (String key:List.of("category","priority","suggestedGroup")) {
            var value=body.path("result").path(key); require(value.isTextual() && valid(value.asText(),256)); result.put(key,value.asText());
        }
        var evidence=new ArrayList<TriageSuggestion.Evidence>();
        require(body.path("evidence").size()>0 && body.path("evidence").size()<=16);
        for (var item:body.path("evidence")) {
            String field=item.path("field").asText(), text=item.path("text").asText();
            require("INPUT".equals(item.path("type").asText()) && Set.of("title","description").contains(field) && !text.isBlank());
            require((field.equals("title") ? input.title():input.description()).contains(text));
            evidence.add(new TriageSuggestion.Evidence(field,text));
        }
        require(body.path("explanation").path("text").isTextual());
        return new TriageSuggestion("SUCCESS",result,evidence,body.path("explanation").path("text").asText(),
            body.path("needsReview").asBoolean(),identity);
    }
    private static void require(boolean value) { if (!value) throw CapabilityException.conflict("Invalid Intelligence response"); }
}
