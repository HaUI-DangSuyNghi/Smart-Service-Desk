package vn.haui.dx.hpdi.process.api;
public record ProcessHandle(String processInstanceId, String processKey, String businessKey, String workspace) {
    @com.fasterxml.jackson.annotation.JsonProperty("id")
    public String id() { return processInstanceId; }
}
