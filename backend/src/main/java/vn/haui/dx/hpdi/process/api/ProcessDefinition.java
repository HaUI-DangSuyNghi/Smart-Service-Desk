package vn.haui.dx.hpdi.process.api;
import java.util.Map;
import java.util.Set;
public record ProcessDefinition(String processKey, Set<String> starterRoles, Set<String> allowedStartVariables,
                                Map<String, Set<String>> outcomesByTask) {
    public ProcessDefinition {
        starterRoles = Set.copyOf(starterRoles);
        allowedStartVariables = Set.copyOf(allowedStartVariables);
        outcomesByTask = outcomesByTask.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }
}
