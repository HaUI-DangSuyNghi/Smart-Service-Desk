package vn.haui.dx.apps.servicedesk;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NewTicket(@NotNull UUID clientRequestId,
                        @NotBlank @Size(max = 200) String title,
                        @NotBlank @Size(max = 4000) String description) {}
