package vn.haui.dx.core.data;

import java.util.Map;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.haui.dx.core.audit.Audit;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import static vn.haui.dx.core.identity.api.WorkspaceRole.AGENT;
import static vn.haui.dx.core.identity.api.WorkspaceRole.COORDINATOR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/core/v1/workspaces/{workspace}/metadata")
public class MetadataApi {
    private final JdbcTemplate jdbc;
    private final Audit audit;
    private final WorkspaceAccess access;
    public MetadataApi(JdbcTemplate jdbc, Audit audit, WorkspaceAccess access) { this.jdbc = jdbc; this.audit = audit; this.access = access; }
    public record Input(@NotBlank @Size(max=200) String title, @NotBlank @Size(max=1000) String source,
                        @NotBlank @Size(max=100) String version, @NotBlank @Size(max=200) String usageRights,
                        @NotBlank @Pattern(regexp="[a-f0-9]{64}") String checksum) {}
    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> create(@PathVariable String workspace, @Valid @RequestBody Input input,
        @AuthenticationPrincipal Jwt jwt, @RequestAttribute String requestId) {
        access.requireRole(workspace, AGENT, COORDINATOR);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into metadata(id,workspace,title,source,version,usage_rights,checksum,owner) values (?,?,?,?,?,?,?,?)",
            id, workspace, input.title(), input.source(), input.version(), input.usageRights(), input.checksum(), jwt.getSubject());
        audit.record(jwt.getSubject(), "metadata.create", id.toString(), workspace, requestId);
        return get(workspace, id, jwt);
    }
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String workspace, @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        access.requireMember(workspace);
        var rows = jdbc.queryForList("select * from metadata where id=? and workspace=?", id, workspace);
        if (rows.isEmpty()) throw new ResponseStatusException(NOT_FOUND);
        return rows.get(0);
    }
}
