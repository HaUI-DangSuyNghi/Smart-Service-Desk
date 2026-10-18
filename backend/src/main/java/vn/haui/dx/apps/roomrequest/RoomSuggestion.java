package vn.haui.dx.apps.roomrequest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record RoomSuggestion(@com.fasterxml.jackson.annotation.JsonValue Map<String, Object> fields) {
    public RoomSuggestion { fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields)); }
}
