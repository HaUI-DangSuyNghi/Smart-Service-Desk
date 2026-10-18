package vn.haui.dx.hpdi.intelligence.integration;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.common.api.CapabilityException;

@Component
public class HttpIntelligenceTransport implements IntelligenceTransport {
    private final String url, token;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper json;
    public HttpIntelligenceTransport(ObjectMapper json, @Value("${dx.intelligence.url:}") String url,
            @Value("${dx.intelligence.service-token:}") String token,
            @Value("${dx.intelligence.timeout-ms:2500}") long timeoutMs) {
        if (timeoutMs<100 || timeoutMs>10000) throw new IllegalArgumentException("Invalid Intelligence timeout");
        this.url=url; this.token=token; this.json=json; this.timeout=Duration.ofMillis(timeoutMs);
        this.http=HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
        if (!url.isBlank()) {
            var uri=URI.create(url);
            if (!java.util.Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null
                    || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || token.length()<32)
                throw new IllegalArgumentException("Intelligence requires a private endpoint and service credential");
        }
    }
    public JsonNode triage(JsonNode envelope) {
        if (url.isBlank()) throw new IntelligenceUnavailable();
        try {
            var request=HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Authorization","Bearer "+token).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(envelope))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            if (response.statusCode()==503) throw new IntelligenceUnavailable();
            if (response.statusCode()!=200 || response.body().length()>262144)
                throw CapabilityException.conflict("Intelligence response rejected");
            try { return json.readTree(response.body()); }
            catch (java.io.IOException malformed) { throw CapabilityException.conflict("Invalid Intelligence response"); }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IntelligenceUnavailable();
        } catch (java.io.IOException error) { throw new IntelligenceUnavailable(); }
    }
}
