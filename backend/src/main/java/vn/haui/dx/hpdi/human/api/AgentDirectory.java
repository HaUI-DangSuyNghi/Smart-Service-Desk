package vn.haui.dx.hpdi.human.api;

import java.util.List;

public interface AgentDirectory {
    List<WorkspaceMember> currentAgents(String workspace, String supportGroup);
}
