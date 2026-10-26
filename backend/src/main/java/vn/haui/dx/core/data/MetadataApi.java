package vn.haui.dx.core.data;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.common.PageRequest;
import vn.haui.dx.core.common.Problems;
import vn.haui.dx.core.data.api.MetadataQuery;
import vn.haui.dx.core.data.api.MetadataRecord;
import vn.haui.dx.core.data.api.MetadataService;
import vn.haui.dx.core.data.api.NewMetadata;
import vn.haui.dx.core.identity.api.RequirePermission;

@RestController
@RequestMapping("/api/core/v1/workspaces/{workspace}/metadata")
public class MetadataApi {
    private final MetadataService metadata;

    public MetadataApi(MetadataService metadata) { this.metadata = metadata; }

    /** JSON trả về dùng camelCase như mọi API khác của Core. */
    public record View(
        UUID id,
        String workspace,
        String title,
        String source,
        String version,
        String usageRights,
        String checksum,
        UUID fileId,
        String owner,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt
    ) {
        public static View of(MetadataRecord record) {
            return new View(record.id(), record.workspace(), record.title(), record.source(), record.version(),
                record.usageRights(), record.checksum(), record.fileId(), record.owner(), record.createdAt());
        }
    }

    @PostMapping
    @RequirePermission("document:upload")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public View create(@PathVariable String workspace, @RequestBody NewMetadata input) {
        return View.of(metadata.create(workspace, input));
    }

    @GetMapping("/{id}")
    @RequirePermission("document:view")
    public View get(@PathVariable String workspace, @PathVariable UUID id) {
        return View.of(metadata.get(workspace, id));
    }

    @GetMapping
    @RequirePermission("document:view")
    public Page<View> search(@PathVariable String workspace, @RequestParam(required = false) String q,
            @RequestParam(required = false) String source, @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        var result = metadata.search(workspace, new MetadataQuery(q, source, sort), PageRequest.of(page, size));
        return new Page<>(result.items().stream().map(View::of).toList(), result.page(), result.size(), result.total());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail invalidRequest() { return Problems.of(ErrorCode.VALIDATION_FAILED, "Invalid metadata request"); }
}
