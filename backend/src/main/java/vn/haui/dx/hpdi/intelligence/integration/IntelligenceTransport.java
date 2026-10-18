package vn.haui.dx.hpdi.intelligence.integration;
import com.fasterxml.jackson.databind.JsonNode;
/** Private adapter SPI; never consumed by domain applications. */
public interface IntelligenceTransport { JsonNode triage(JsonNode envelope); }
