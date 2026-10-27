package vn.haui.dx.apps.servicedesk.clarification;

import java.util.List;
import java.util.UUID;

public record ClarificationRequest(
        String contractVersion,
        UUID clientAnswerId,
        Long expectedContextRevision,
        List<Answer> answers) {

    public static final String VERSION = "ticket-clarification@0.1.0";

    /** correction phải được người gửi xác nhận; model không được tạo giá trị này. */
    public record Answer(String slot, String value, boolean correction) {}
}
