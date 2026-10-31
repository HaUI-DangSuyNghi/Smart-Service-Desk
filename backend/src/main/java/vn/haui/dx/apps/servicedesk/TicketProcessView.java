package vn.haui.dx.apps.servicedesk;

/** Application-owned correlation fields; process identifiers are opaque. */
public record TicketProcessView(String processInstanceId, String processKey, String businessKey, String workspace) {
    @com.fasterxml.jackson.annotation.JsonProperty("id")
    public String id() { return processInstanceId; }
}
