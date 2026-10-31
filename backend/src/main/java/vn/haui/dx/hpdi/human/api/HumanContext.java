package vn.haui.dx.hpdi.human.api;
public interface HumanContext {
    default Actor currentMember(String workspace) {
        throw vn.haui.dx.hpdi.common.api.CapabilityException.forbidden("Current membership verification is not supported");
    }
    Actor currentActor(String workspace);
}
