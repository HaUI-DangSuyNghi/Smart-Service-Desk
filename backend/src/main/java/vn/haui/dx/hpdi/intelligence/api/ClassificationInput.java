package vn.haui.dx.hpdi.intelligence.api;

import java.util.List;
import java.util.Map;

public record ClassificationInput(
        Map<String, String> fields,
        List<String> allowedClasses,
        String modelDigest,
        String policyDigest) {

    public ClassificationInput {
        fields = Map.copyOf(fields);
        allowedClasses = List.copyOf(allowedClasses);
    }
}
