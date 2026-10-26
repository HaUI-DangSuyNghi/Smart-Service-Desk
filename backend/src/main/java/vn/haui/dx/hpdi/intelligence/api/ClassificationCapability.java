package vn.haui.dx.hpdi.intelligence.api;

/** Bounded inference only. The caller owns labels, policy and operational disposition. */
public interface ClassificationCapability {
    ClassificationObservation observe(String workspace, ClassificationInput input);
}
