package vn.haui.dx.hpdi.process.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nhãn hiển thị của một quy trình: tên quy trình, tên bước, kết quả, biến khởi tạo.
 * Khai báo ở Java cạnh BPMN thay vì sửa {@code name} trong BPMN: sửa BPMN sinh phiên bản mới,
 * instance đang chạy bị khóa. Thứ tự khai báo được giữ và dùng làm thứ tự hiển thị.
 */
public record ProcessLabels(String name, Map<String, String> tasks, Map<String, String> outcomes,
                            Map<String, String> variables) {
    public static final ProcessLabels NONE = new ProcessLabels(null, Map.of(), Map.of(), Map.of());

    public ProcessLabels {
        tasks = ordered(tasks);
        outcomes = ordered(outcomes);
        variables = ordered(variables);
    }

    public static ProcessLabels named(String name) {
        return new ProcessLabels(text(name), Map.of(), Map.of(), Map.of());
    }

    public ProcessLabels task(String key, String label) {
        return new ProcessLabels(name, with(tasks, key, label), outcomes, variables);
    }

    public ProcessLabels outcome(String key, String label) {
        return new ProcessLabels(name, tasks, with(outcomes, key, label), variables);
    }

    public ProcessLabels variable(String key, String label) {
        return new ProcessLabels(name, tasks, outcomes, with(variables, key, label));
    }

    private static Map<String, String> with(Map<String, String> source, String key, String label) {
        var copy = new LinkedHashMap<>(source);
        copy.put(text(key), text(label));
        return copy;
    }

    private static Map<String, String> ordered(Map<String, String> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Label key and text are required");
        return value;
    }
}
