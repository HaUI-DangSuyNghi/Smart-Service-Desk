package vn.haui.dx.core.workflow.api;

/** Kết quả khởi tạo quy trình. */
public record ProcessRef(String processInstanceId, String processKey, String businessKey, String workspace) {
    @com.fasterxml.jackson.annotation.JsonProperty("id")
    public String id() { return processInstanceId; }
}
