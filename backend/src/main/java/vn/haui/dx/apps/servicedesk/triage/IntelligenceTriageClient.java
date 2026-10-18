package vn.haui.dx.apps.servicedesk.triage;

import com.fasterxml.jackson.databind.JsonNode;

public interface IntelligenceTriageClient {
    JsonNode triage(TriageRequest request);
}
