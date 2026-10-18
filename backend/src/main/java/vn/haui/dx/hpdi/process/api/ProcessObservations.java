package vn.haui.dx.hpdi.process.api;
import java.util.List;
import java.util.Set;
public interface ProcessObservations { List<ProcessSnapshot> snapshots(String workspace, Set<String> ids); }
