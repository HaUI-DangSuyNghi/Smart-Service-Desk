package vn.haui.dx.hpdi.intelligence.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validated capability result. Contains no HTTP, engine, or Core DTO types. */
public record TriageSuggestion(Map<String, Object> attributes) {
    public TriageSuggestion { attributes = immutable(attributes); }
    private static Map<String, Object> immutable(Map<String, Object> source) {
        var result = new LinkedHashMap<String, Object>();
        source.forEach((key, value) -> result.put(key, freeze(value)));
        return Collections.unmodifiableMap(result);
    }
    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> result.put((String) key, freeze(item)));
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list) return list.stream().map(TriageSuggestion::freeze).toList();
        return value;
    }
}
