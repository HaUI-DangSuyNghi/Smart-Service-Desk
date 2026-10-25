package vn.haui.dx.apps.servicedesk.intelligence;

import vn.haui.dx.hpdi.common.api.CapabilityException;

public record TicketAssessmentRequest(
        String contractVersion,
        String question,
        boolean includeGuidance) {

    public TicketAssessmentRequest {
        if (!"0.1.0".equals(contractVersion)) {
            throw CapabilityException.validation("Unsupported assistant contract version");
        }

        if (question != null && (question.isBlank()
                || question.length() > 2000
                || question.codePoints().anyMatch(value -> Character.isISOControl(value)
                        && value != '\n' && value != '\r' && value != '\t'))) {
            throw CapabilityException.validation("A question must contain 1 to 2000 characters");
        }
    }
}
