package vn.haui.dx.hpdi.intelligence.api;

/** A category vote carries neither calibrated confidence nor action authorization. */
public record ClassificationObservation(
        String decision,
        String classId,
        String modelDigest,
        String policyDigest,
        String evidenceStatus,
        String scoreSemantics,
        boolean operationalAuthority) {}
