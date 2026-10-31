package vn.haui.dx.hpdi.intelligence.integration;

import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

import com.fasterxml.jackson.databind.JsonNode;

public interface IntelligenceTransport {
    JsonNode triage(TriageRequest.Envelope request);
}
