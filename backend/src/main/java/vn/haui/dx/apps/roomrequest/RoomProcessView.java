package vn.haui.dx.apps.roomrequest;

public record RoomProcessView(String processInstanceId, String processKey, String businessKey, String workspace) {
    @com.fasterxml.jackson.annotation.JsonProperty("id") public String id() { return processInstanceId; }
}
