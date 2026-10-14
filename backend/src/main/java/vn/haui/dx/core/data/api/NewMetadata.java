package vn.haui.dx.core.data.api;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dữ liệu tạo metadata. Không có {@code fileId} thì bắt buộc {@code checksum}; có {@code fileId} thì server tự tính. */
public record NewMetadata(@NotBlank @Size(max=200) String title, @NotBlank @Size(max=1000) String source,
                          @NotBlank @Size(max=100) String version, @NotBlank @Size(max=200) String usageRights,
                          @Pattern(regexp="[a-f0-9]{64}") String checksum, UUID fileId) {}
