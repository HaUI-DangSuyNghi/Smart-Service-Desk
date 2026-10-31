package vn.haui.dx.hpdi.human.api;

import java.time.Instant;
import java.util.Map;

/**
 * Một thao tác người dùng đã làm, lấy từ nhật ký audit.
 *
 * @param actor subject người thực hiện
 * @param action dạng {@code <đối tượng>.<động từ>}, ví dụ {@code task.complete}; app có tiền tố riêng
 * @param processInstanceId quy trình liên quan (để mở trang tiến độ); null nếu không thuộc quy trình
 * @param processKey loại quy trình, để giao diện tra nhãn tiếng Việt; null nếu không xác định được
 * @param details tham chiếu an toàn của thao tác (taskKey, outcome...), chỉ chuỗi
 */
public record ActivityEntry(String id, Instant occurredAt, String actor, String action, String resourceType, String resource,
                            String processInstanceId, String processKey, Map<String, String> details) {
    public ActivityEntry { details = Map.copyOf(details); }
}
