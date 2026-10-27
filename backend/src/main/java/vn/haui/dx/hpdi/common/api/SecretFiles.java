package vn.haui.dx.hpdi.common.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Reads a bounded machine-local secret without including its value or path in errors. */
public final class SecretFiles {
    private SecretFiles() {}

    public static String resolve(String inline, String filename) {
        if (filename == null || filename.isBlank()) return inline;
        if (inline != null && !inline.isBlank()) throw new IllegalArgumentException("Conflicting secret sources");
        try {
            var path = Path.of(filename);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 8192) {
                throw new IllegalArgumentException("Invalid secret file");
            }
            try (var input = Files.newInputStream(path)) {
                byte[] bytes = input.readNBytes(8193);
                if (bytes.length > 8192) throw new IllegalArgumentException("Invalid secret file");
                String value = new String(bytes, StandardCharsets.US_ASCII).stripTrailing();
                if (value.isBlank() || value.codePoints().anyMatch(code -> code < 33 || code > 126)) {
                    throw new IllegalArgumentException("Invalid secret file");
                }
                return value;
            }
        } catch (Exception failure) {
            throw new IllegalArgumentException("Secret file unavailable or invalid");
        }
    }
}
