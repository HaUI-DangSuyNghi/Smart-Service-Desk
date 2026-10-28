package vn.haui.dx.apps.servicedesk.intelligence.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import static org.junit.jupiter.api.Assertions.*;

/** Chỉ dùng trong test để kiểm response thật với contract additive. */
public final class ContextSchemaAssertions {
    private ContextSchemaAssertions() {}

    public static void verify(ObjectMapper json, String name, JsonNode value) throws Exception {
        try (var stream = ContextSchemaAssertions.class.getResourceAsStream("/servicedesk-contract/" + name)) {
            assertNotNull(stream);
            var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(stream));
            assertTrue(schema.validate(value).isEmpty(), () -> schema.validate(value).toString());
        }
    }
}
