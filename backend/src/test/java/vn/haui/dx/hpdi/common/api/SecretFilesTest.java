package vn.haui.dx.hpdi.common.api;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SecretFilesTest {
    @TempDir Path directory;

    @Test
    void acceptsDockerSecretAndRejectsAmbiguousOrInvalidSources() throws Exception {
        var file = directory.resolve("token");
        Files.writeString(file, "a".repeat(64) + "\n");
        assertEquals("a".repeat(64), SecretFiles.resolve("", file.toString()));
        assertEquals("legacy", SecretFiles.resolve("legacy", ""));
        assertThrows(IllegalArgumentException.class, () -> SecretFiles.resolve("inline", file.toString()));
        for (String value : new String[]{"", "two words", "abc\ndef", "x".repeat(8193), "\u00e9"}) {
            Files.writeString(file, value);
            assertThrows(IllegalArgumentException.class, () -> SecretFiles.resolve("", file.toString()));
        }
        assertThrows(IllegalArgumentException.class, () -> SecretFiles.resolve("", directory.toString()));
        assertThrows(IllegalArgumentException.class, () -> SecretFiles.resolve("", directory.resolve("missing").toString()));
    }
}
