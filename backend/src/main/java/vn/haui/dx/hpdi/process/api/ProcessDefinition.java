package vn.haui.dx.hpdi.process.api;
import java.util.Map;
import java.util.Set;
public record ProcessDefinition(String processKey, Set<String> starterRoles, Set<String> allowedStartVariables,
                                Map<String, Set<String>> outcomesByTask, Set<String> observerRoles, Set<String> readerRoles) {
    public ProcessDefinition(String key, Set<String> starters, Set<String> variables, Map<String, Set<String>> outcomes) {
        this(key, starters, variables, outcomes, Set.of(), Set.of());
    }
    public ProcessDefinition {
        starterRoles = Set.copyOf(starterRoles);
        observerRoles = Set.copyOf(observerRoles);
        readerRoles = Set.copyOf(readerRoles);
        allowedStartVariables = Set.copyOf(allowedStartVariables);
        outcomesByTask = outcomesByTask.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }
}
