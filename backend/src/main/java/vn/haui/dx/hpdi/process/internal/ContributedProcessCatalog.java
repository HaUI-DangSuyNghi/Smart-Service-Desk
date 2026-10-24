package vn.haui.dx.hpdi.process.internal;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.process.api.*;
// Import rõ ràng: java.lang cũng có ProcessHandle, import * không đủ để phân biệt
import vn.haui.dx.hpdi.process.api.ProcessHandle;

/** Danh mục dựng từ ProcessContribution của các app; không biết app nào hay quy trình nào cụ thể. */
@Service
public class ContributedProcessCatalog implements ProcessCatalog {
    /** Ô một dòng của form chung; Core cho mọi biến tới 2000 ký tự, chỉ ô nhiều dòng được dài hơn. */
    static final int SHORT_TEXT = 200;
    private final Map<String, ProcessDefinition> definitions;
    private final HumanContext human;
    private final ProcessCapability processes;

    public ContributedProcessCatalog(List<ProcessContribution> contributions, HumanContext human,
                                     ProcessCapability processes) {
        this.definitions = contributions.stream().flatMap(contribution -> contribution.definitions().stream())
            .collect(Collectors.toUnmodifiableMap(ProcessDefinition::processKey, Function.identity()));
        this.human = human;
        this.processes = processes;
    }

    @Override
    public List<ProcessDescriptor> describe(String workspace) {
        // currentActor tự kiểm thành viên workspace; vai trò chỉ dùng để tính canStart
        var roles = human.currentActor(workspace).roles();
        return definitions.values().stream().map(definition -> describe(definition, roles))
            .sorted(Comparator.comparing(ProcessDescriptor::name)).toList();
    }

    @Override
    public ProcessHandle startSelfService(String workspace, String processKey, Map<String, String> variables) {
        var definition = definitions.get(processKey);
        if (definition == null) throw CapabilityException.notFound("Process not found");
        if (!definition.selfService()) throw CapabilityException.forbidden("Process must be started from its app");
        var multiline = definition.labels().multiline();
        if (variables != null && variables.entrySet().stream().anyMatch(entry -> entry.getValue() != null
                && entry.getValue().length() > SHORT_TEXT && !multiline.contains(entry.getKey())))
            throw CapabilityException.validation("Start variable too long");
        // Vai trò khởi tạo và tên biến hợp lệ do Core kiểm khi start
        var input = new HashMap<String, Object>(variables == null ? Map.of() : variables);
        return processes.start(workspace, processKey, UUID.randomUUID().toString(), input);
    }

    private static ProcessDescriptor describe(ProcessDefinition definition, Set<String> roles) {
        var labels = definition.labels();
        var tasks = ordered(labels.tasks().keySet(), definition.outcomesByTask().keySet()).stream()
            .map(task -> new ProcessDescriptor.Task(task, labels.tasks().getOrDefault(task, task),
                options(labels.outcomes(), definition.outcomesByTask().get(task))))
            .toList();
        boolean canStart = definition.selfService() && definition.starterRoles().stream().anyMatch(roles::contains);
        String name = labels.name() == null ? definition.processKey() : labels.name();
        return new ProcessDescriptor(definition.processKey(), name, canStart, tasks,
            fields(labels, definition.allowedStartVariables()));
    }

    private static List<ProcessDescriptor.Option> options(Map<String, String> labels, Set<String> keys) {
        return ordered(labels.keySet(), keys).stream()
            .map(key -> new ProcessDescriptor.Option(key, labels.getOrDefault(key, key))).toList();
    }

    private static List<ProcessDescriptor.Field> fields(ProcessLabels labels, Set<String> keys) {
        return ordered(labels.variables().keySet(), keys).stream()
            .map(key -> new ProcessDescriptor.Field(key, labels.variables().getOrDefault(key, key),
                labels.multiline().contains(key))).toList();
    }

    /** Key có nhãn theo thứ tự khai báo nhãn, sau đó key chưa có nhãn theo bảng chữ cái. */
    private static List<String> ordered(Set<String> labelled, Set<String> keys) {
        return Stream.concat(labelled.stream().filter(keys::contains),
            keys.stream().filter(key -> !labelled.contains(key)).sorted()).toList();
    }
}
