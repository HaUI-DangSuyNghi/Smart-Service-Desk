package vn.haui.dx.hpdi.intelligence.internal;

import org.springframework.stereotype.Service;

import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.intelligence.api.ClassificationCapability;
import vn.haui.dx.hpdi.intelligence.api.ClassificationInput;
import vn.haui.dx.hpdi.intelligence.api.ClassificationObservation;
import vn.haui.dx.hpdi.intelligence.integration.ClassificationTransport;

@Service
public class TrustedClassificationCapability implements ClassificationCapability {
    private final HumanContext human;
    private final ClassificationTransport transport;

    public TrustedClassificationCapability(HumanContext human, ClassificationTransport transport) {
        this.human = human;
        this.transport = transport;
    }

    @Override
    public ClassificationObservation observe(String workspace, ClassificationInput input) {
        var actor = human.currentActor(workspace);
        if (actor == null
                || !workspace.equals(actor.workspace())
                || actor.subject() == null
                || actor.subject().isBlank()) {
            throw CapabilityException.forbidden("Trusted workspace actor required");
        }

        var result = transport.observe(input);
        if (!actor.equals(human.currentActor(workspace))) {
            throw CapabilityException.forbidden("Actor changed during inference");
        }
        return result;
    }
}
