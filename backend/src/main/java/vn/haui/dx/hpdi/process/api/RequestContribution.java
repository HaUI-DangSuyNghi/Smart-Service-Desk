package vn.haui.dx.hpdi.process.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.haui.dx.hpdi.human.api.Actor;

/** Applications contribute fields and policy; Tier 2 owns authorization, CAS, history and delivery. */
public interface RequestContribution {
    record Context(ProcessTimeline process, Actor actor) {}
    record Field(String key, String label, String value, int maxLength, boolean multiline) {}
    record Form(String revision, boolean editable, boolean cancellable, boolean closable, String explanation, List<Field> fields) {
        public Form { fields = List.copyOf(fields); }
        public Form(String revision, boolean editable, boolean cancellable, String explanation, List<Field> fields) {
            this(revision, editable, cancellable, false, explanation, fields);
        }
    }
    Set<String> processKeys();
    default boolean ownerOnly() { return false; }
    Form describe(Context context);
    /** Must persist only application-owned data, returning approved start-variable changes. */
    Map<String, Object> update(Context context, Form expected, Map<String, String> values, String reason);
    void cancel(Context context, Form expected, String reason);
    default void close(Context context, Form expected, String reason) {
        throw vn.haui.dx.hpdi.common.api.CapabilityException.conflict("Request cannot be closed");
    }
}
