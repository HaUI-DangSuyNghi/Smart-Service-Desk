package vn.haui.dx.core.common;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonSecurityRegressionTest {
    @Test void malformedDataInputTokenHasBoundedDiagnostic() throws Exception {
        byte[] input = ("x".repeat(8192) + " ").getBytes(StandardCharsets.UTF_8);
        try (var stream = new DataInputStream(new ByteArrayInputStream(input));
             var parser = new JsonFactory().createParser((DataInput) stream)) {
            var failure = assertThrows(JsonParseException.class, parser::nextToken);
            assertTrue(failure.getOriginalMessage().length() < 1024,
                "Malformed token diagnostics must not grow with untrusted input (CVE-2026-89425)");
        }
    }
}
