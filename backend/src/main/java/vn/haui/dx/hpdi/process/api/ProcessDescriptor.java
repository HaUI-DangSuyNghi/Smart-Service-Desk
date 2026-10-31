package vn.haui.dx.hpdi.process.api;

import java.util.List;

/**
 * Quy trình nhìn từ giao diện chung: nhãn, kết quả hợp lệ của từng bước, biến cần nhập khi tạo.
 *
 * @param canStart người gọi được tạo yêu cầu từ form chung (quy trình tự phục vụ và có vai trò khởi tạo)
 */
public record ProcessDescriptor(String processKey, String name, boolean canStart, List<Task> tasks,
                                List<Field> startFields) {
    public ProcessDescriptor {
        tasks = List.copyOf(tasks);
        startFields = List.copyOf(startFields);
    }

    /** Một bước (userTask) và các kết quả được chọn khi hoàn thành. */
    public record Task(String key, String name, List<Option> outcomes) {
        public Task { outcomes = List.copyOf(outcomes); }
    }

    /** Cặp key gửi lên server và nhãn hiển thị. */
    public record Option(String key, String label) {}

    /** Biến nhập khi tạo; {@code multiline}: ô nhiều dòng tối đa 2000 ký tự, ô thường 200. */
    public record Field(String key, String label, boolean multiline) {}
}
