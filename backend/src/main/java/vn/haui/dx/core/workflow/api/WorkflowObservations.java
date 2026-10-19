package vn.haui.dx.core.workflow.api;
import java.util.List;
import java.util.Set;
/** Batch technical state: own processes, or workspace agent/coordinator; absent/unreadable IDs are omitted. */
public interface WorkflowObservations { List<ProcessSnapshot> snapshots(String workspace, Set<String> ids); }
