package vn.haui.dx.hpdi.process.api;
import java.util.Map;
/** Workspace-scoped requests and timelines with app-independent process references. */
public interface ProcessCapability {
    ProcessHandle start(String workspace, String processKey, String businessKey, Map<String, Object> variables);
    ProcessTimeline timeline(String workspace, String processInstanceId);
}
