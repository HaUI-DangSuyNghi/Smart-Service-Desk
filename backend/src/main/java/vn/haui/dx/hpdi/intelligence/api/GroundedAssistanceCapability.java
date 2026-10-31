package vn.haui.dx.hpdi.intelligence.api;

/** Read-only, advisory assistance over server-selected knowledge pins. */
public interface GroundedAssistanceCapability {
    AssistanceOutcome assist(String workspace, AssistanceInput input);
}
