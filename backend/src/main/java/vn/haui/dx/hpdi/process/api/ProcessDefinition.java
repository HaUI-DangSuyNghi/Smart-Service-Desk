package vn.haui.dx.hpdi.process.api;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Luật và nhãn của một quy trình do app khai báo.
 *
 * @param selfService cho phép tạo từ form chung của Tầng 2. Chỉ bật cho quy trình không gắn bản ghi nghiệp vụ;
 *        quy trình như ticket phải tạo qua API của app để bản ghi và process ra đời cùng lúc
 * @param labels nhãn hiển thị; thiếu nhãn thì giao diện dùng key
 */
public record ProcessDefinition(String processKey, Set<String> starterRoles, Set<String> allowedStartVariables,
                                Map<String, Set<String>> outcomesByTask, Set<String> observerRoles, Set<String> readerRoles,
                                boolean selfService, ProcessLabels labels) {
    public ProcessDefinition(String key, Set<String> starters, Set<String> variables, Map<String, Set<String>> outcomes) {
        this(key, starters, variables, outcomes, Set.of(), Set.of());
    }

    public ProcessDefinition(String key, Set<String> starters, Set<String> variables, Map<String, Set<String>> outcomes,
                             Set<String> observers, Set<String> readers) {
        this(key, starters, variables, outcomes, observers, readers, false, ProcessLabels.NONE);
    }

    public ProcessDefinition {
        starterRoles = Set.copyOf(starterRoles);
        observerRoles = Set.copyOf(observerRoles);
        readerRoles = Set.copyOf(readerRoles);
        allowedStartVariables = Set.copyOf(allowedStartVariables);
        outcomesByTask = outcomesByTask.entrySet().stream().collect(Collectors.toUnmodifiableMap(
            Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
        labels = labels == null ? ProcessLabels.NONE : labels;
        requireKnownLabels(processKey, outcomesByTask, allowedStartVariables, labels);
    }

    /** Cho phép tạo từ form chung của Tầng 2 (xem {@code selfService}). */
    public ProcessDefinition allowSelfService() {
        return new ProcessDefinition(processKey, starterRoles, allowedStartVariables, outcomesByTask,
            observerRoles, readerRoles, true, labels);
    }

    public ProcessDefinition withLabels(ProcessLabels labels) {
        return new ProcessDefinition(processKey, starterRoles, allowedStartVariables, outcomesByTask,
            observerRoles, readerRoles, selfService, labels);
    }

    // Gõ sai key nhãn thì báo ngay lúc khởi động, thay vì giao diện âm thầm hiện key thô
    private static void requireKnownLabels(String processKey, Map<String, Set<String>> outcomesByTask,
                                           Set<String> variables, ProcessLabels labels) {
        var outcomes = outcomesByTask.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        unknown(processKey, "task", labels.tasks().keySet(), outcomesByTask.keySet());
        unknown(processKey, "outcome", labels.outcomes().keySet(), outcomes);
        unknown(processKey, "variable", labels.variables().keySet(), variables);
    }

    private static void unknown(String processKey, String kind, Set<String> labelled, Set<String> known) {
        labelled.stream().filter(key -> !known.contains(key)).findFirst().ifPresent(key -> {
            throw new IllegalArgumentException("Label for unknown " + kind + " '" + key + "' in " + processKey);
        });
    }
}
