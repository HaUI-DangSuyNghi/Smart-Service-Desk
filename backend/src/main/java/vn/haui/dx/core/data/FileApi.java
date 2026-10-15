package vn.haui.dx.core.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.haui.dx.core.data.api.FileService;
import vn.haui.dx.core.data.api.StoredFile;
import vn.haui.dx.core.common.CoreException;
import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/core/v1/workspaces/{workspace}/files")
public class FileApi {
    private final FileService files;
    private final boolean allowUnscannedUpload;
    public FileApi(FileService files, @Value("${dx.storage.allow-unscanned-upload:false}") boolean allowUnscannedUpload) {
        this.files = files;
        this.allowUnscannedUpload = allowUnscannedUpload;
    }

    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(CREATED)
    public StoredFile upload(@PathVariable String workspace, @RequestParam MultipartFile file) throws IOException {
        if (!allowUnscannedUpload) throw CoreException.forbidden("Uploads disabled pending content-scanning approval");
        try (var content = file.getInputStream()) {
            return files.store(workspace, file.getOriginalFilename(), file.getContentType(), content);
        }
    }
    @GetMapping("/{fileId}")
    public StoredFile describe(@PathVariable String workspace, @PathVariable UUID fileId) {
        return files.describe(workspace, fileId);
    }
    @GetMapping("/{fileId}/content")
    public ResponseEntity<Resource> download(@PathVariable String workspace, @PathVariable UUID fileId) {
        var file = files.describe(workspace, fileId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.mediaType()))
            .contentLength(file.size())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.originalName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(new InputStreamResource(files.open(workspace, fileId)));
    }
}
