package vn.haui.dx.hpdi.intelligence.integration;

import vn.haui.dx.hpdi.intelligence.api.ClassificationInput;
import vn.haui.dx.hpdi.intelligence.api.ClassificationObservation;

public interface ClassificationTransport {
    ClassificationObservation observe(ClassificationInput input);
}
