package vn.haui.dx.apps.servicedesk.triage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Application-owned response for the existing Service Desk triage endpoint. */
public record TicketTriageView(@com.fasterxml.jackson.annotation.JsonValue Map<String, Object> fields) {
    public TicketTriageView { fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields)); }
}
