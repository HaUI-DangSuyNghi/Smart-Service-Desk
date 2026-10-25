package vn.haui.dx.apps.servicedesk.intelligence;

import vn.haui.dx.apps.servicedesk.intelligence.TicketIntelligenceView.Explanation;

/** Explanations describe the rule decision; they do not assert a diagnosis. */
final class TicketExplanationRenderer {

    private TicketExplanationRenderer() {}

    static Explanation render(
            String category,
            String priority,
            String impact,
            String urgency,
            String reasonCode) {

        if ("INSUFFICIENT_INFORMATION".equals(reasonCode)) {
            return new Explanation(
                    "Chưa đủ thông tin để phân loại; OTHER là nhãn dự phòng cần xác nhận.",
                    "MEDIUM là mức dự phòng, chưa phải đánh giá ưu tiên đã xác nhận.");
        }

        String classification = "Các quy tắc và bằng chứng đầu vào gợi ý nhóm " + category + ".";

        if ("ROOT_CAUSE_CONFLICT".equals(reasonCode)) {
            classification = "Chính sách xử lý tín hiệu xung đột gợi ý nhóm " + category + ".";
        }

        return new Explanation(
                classification + " Người xử lý cần xác nhận.",
                "Mức " + priority + " theo ma trận tác động " + impact
                        + " và khẩn cấp " + urgency + ".");
    }
}
