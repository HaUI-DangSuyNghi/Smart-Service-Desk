package vn.haui.dx.core.workflow.api;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import vn.haui.dx.core.identity.api.WorkspaceRole;

/**
 * Luật của một quy trình BPMN, khai báo qua {@link ProcessRegistration}.
 *
 * @param processKey id process trong BPMN, khớp file {@code processes/<processKey>.bpmn20.xml}
 * @param starterRoles vai trò được khởi tạo
 * @param allowedStartVariables tên biến client được truyền khi start (chuỗi ≤ 200 ký tự);
 *        {@code initiator}, {@code workspace} luôn do server đặt
 * @param outcomesByTask taskKey → outcome hợp lệ, ví dụ {@code review → {approved, rejected}}
 */
public record ProcessPolicy(String processKey, Set<WorkspaceRole> starterRoles, Set<String> allowedStartVariables,
                            Map<String, Set<String>> outcomesByTask, Set<WorkspaceRole> observerRoles) {
    public ProcessPolicy(String key, Set<WorkspaceRole> starters, Set<String> variables,
                         Map<String, Set<String>> outcomes) {
        this(key, starters, variables, outcomes, Set.of());
    }
    public ProcessPolicy {
        if (processKey == null || processKey.isBlank()) throw new IllegalArgumentException("processKey is required");
        if (starterRoles == null || starterRoles.isEmpty()) throw new IllegalArgumentException("starterRoles is required");
        starterRoles = Set.copyOf(starterRoles);
        observerRoles = Set.copyOf(observerRoles);
        allowedStartVariables = Set.copyOf(allowedStartVariables);
        outcomesByTask = outcomesByTask.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }
}
