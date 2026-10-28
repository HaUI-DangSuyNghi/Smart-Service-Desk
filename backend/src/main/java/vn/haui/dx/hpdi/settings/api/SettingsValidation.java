package vn.haui.dx.hpdi.settings.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import vn.haui.dx.hpdi.common.api.CapabilityException;

public final class SettingsValidation {
    private SettingsValidation() {}
    public static ObjectNode parse(ObjectMapper json, String body) {
        try {
            JsonNode node = json.reader().with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
            require(node != null && node.isObject(), "JSON object required");
            return (ObjectNode) node;
        } catch (IOException failure) { throw CapabilityException.validation("Invalid settings JSON"); }
    }
    public static void object(JsonNode node, String... fields) {
        require(node != null && node.isObject(), "Object required");
        var actual = new HashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(Set.of(fields)), "Missing or unknown settings fields");
    }
    public static long integer(JsonNode node, long min, long max) {
        require(node != null && node.isIntegralNumber() && node.canConvertToLong(), "Integer required");
        long value = node.longValue();
        require(value >= min && value <= max, "Integer outside supported range");
        return value;
    }
    public static void positive(JsonNode node) { integer(node, 1, Integer.MAX_VALUE); }
    public static void ratio(JsonNode node) {
        require(node != null && node.isNumber() && Double.isFinite(node.doubleValue())
            && node.decimalValue().signum() >= 0 && node.decimalValue().compareTo(java.math.BigDecimal.ONE) <= 0,
            "Ratio must be between zero and one");
    }
    public static String text(JsonNode node, int max) {
        require(node != null && node.isTextual() && !node.textValue().isBlank()
            && node.textValue().length() <= max, "Nonblank bounded string required");
        return node.textValue();
    }
    public static void require(boolean condition, String message) {
        if (!condition) throw CapabilityException.validation(message);
    }
}
