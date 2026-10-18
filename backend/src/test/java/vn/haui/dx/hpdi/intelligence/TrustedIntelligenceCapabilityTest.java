package vn.haui.dx.hpdi.intelligence;
import java.util.Set;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.intelligence.api.*;
import vn.haui.dx.hpdi.intelligence.internal.TrustedIntelligenceCapability;
import vn.haui.dx.hpdi.intelligence.integration.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TrustedIntelligenceCapabilityTest {
    private final ObjectMapper json=new ObjectMapper();
    private final HumanContext human=mock(HumanContext.class);
    private final AuditService audit=mock(AuditService.class);
    private final TriageInput input=new TriageInput("arbitrary-domain","0.2.0","Room booking","One request");
    private JsonNode response(JsonNode envelope) {
        var request=envelope.path("request");
        var result=request.deepCopy(); var body=(com.fasterxml.jackson.databind.node.ObjectNode)result;
        body.remove("context"); body.remove("input");
        body.put("workspaceId","lab-a").put("status","SUCCESS").put("needsReview",false).putNull("error");
        body.putObject("result").put("category","BOOKING").put("priority","NORMAL").put("suggestedGroup","FACILITIES");
        body.putObject("runtime").put("engine","rules").put("profileVersion","arbitrary-domain@0.2.0").put("rulesVersion","0.2.0");
        body.putObject("explanation").put("text","Profile suggestion");
        body.putArray("evidence").addObject().put("type","INPUT").put("field","title").put("text","Room");
        return body;
    }
    private void actor() { when(human.currentActor("lab-a")).thenReturn(new Actor("verified-subject","lab-a",Set.of("requester"),Set.of())); }
    @Test void authorizationPrecedesAnyTransportOrAudit() {
        var transport=mock(IntelligenceTransport.class);
        when(human.currentActor("lab-a")).thenThrow(CapabilityException.forbidden("Denied"));
        assertThrows(CapabilityException.class,()->new TrustedIntelligenceCapability(human,transport,json,audit).suggest("lab-a",input));
        verifyNoInteractions(transport,audit);
    }
    @Test void trustedContextIsServerDerivedAndInputIsNeverUsedAsEvidenceWithoutVerification() {
        actor();
        IntelligenceTransport transport=envelope->{
            assertEquals("verified-subject",envelope.path("trustedContext").path("subject").asText());
            assertEquals("lab-a",envelope.path("trustedContext").path("workspaceId").asText());
            return response(envelope);
        };
        var result=new TrustedIntelligenceCapability(human,transport,json,audit).suggest("lab-a",input);
        assertEquals("BOOKING",result.result().get("category"));
        assertEquals("Room",result.evidence().get(0).text());
    }
    @Test void operationalFailureDegradesButForeignResponseFailsClosed() {
        actor();
        var unavailable=new TrustedIntelligenceCapability(human,envelope->{throw new IntelligenceUnavailable();},json,audit);
        assertEquals("DEGRADED",unavailable.suggest("lab-a",input).status());
        IntelligenceTransport foreign=envelope->{var body=(com.fasterxml.jackson.databind.node.ObjectNode)response(envelope);body.put("workspaceId","lab-b");return body;};
        assertThrows(CapabilityException.class,()->new TrustedIntelligenceCapability(human,foreign,json,audit).suggest("lab-a",input));
        IntelligenceTransport invented=envelope->{var body=response(envelope);((com.fasterxml.jackson.databind.node.ObjectNode)body.path("evidence").get(0)).put("text","fabricated");return body;};
        assertThrows(CapabilityException.class,()->new TrustedIntelligenceCapability(human,invented,json,audit).suggest("lab-a",input));
    }
    @Test void privateHttpClientDoesNotDegradeAuthenticationFailuresOrFollowRedirects() throws Exception {
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/triage",exchange->{exchange.sendResponseHeaders(401,-1);exchange.close();});
        server.start();
        try {
            var client=new HttpIntelligenceTransport(json,"http://127.0.0.1:"+server.getAddress().getPort()+"/v1/triage","s".repeat(32),500);
            assertThrows(CapabilityException.class,()->client.triage(json.createObjectNode()));
        } finally { server.stop(0); }
    }
}
